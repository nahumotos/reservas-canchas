package com.nahumotos.pagos_service.reembolso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import com.nahumotos.pagos_service.PruebaDeIntegracion;
import com.nahumotos.pagos_service.error.ServicioNoDisponibleException;
import com.nahumotos.pagos_service.eventos.RabbitConfig;
import com.nahumotos.pagos_service.mercadopago.OrderMp;
import com.nahumotos.pagos_service.pago.EstadoPago;
import com.nahumotos.pagos_service.pago.Pago;
import com.nahumotos.pagos_service.pago.PagoRepository;

/**
 * reserva.rechazada y reserva.cancelada llegan por RabbitMQ (como los publicaría
 * reservas-service) y terminan en un reembolso en Mercado Pago (simulado). Escenario: el pago de
 * la reserva 7 está APROBADO con la order {@value #ORDER_ID}.
 */
class ReembolsoIntegrationTest extends PruebaDeIntegracion {

	private static final String ORDER_ID = "ORD01JQ4S4KYYWFQFM2FF4XRTA1MX";
	private static final Duration ESPERA = Duration.ofSeconds(10);

	@Autowired
	private PagoRepository pagoRepository;

	@Autowired
	private ReintentoDeReembolsos reintento;

	@Autowired
	private RabbitTemplate rabbit;

	private Pago pago;

	@BeforeEach
	void pagoAprobado() {
		pago = pagoAprobado(7L, ORDER_ID);
		when(mercadoPago.reembolsar(any(), any())).thenReturn(true);
	}

	@Test
	void reservaRechazada_reembolsaElPagoConSuClaveFija() {
		publicar("reserva.rechazada", rechazada(UUID.randomUUID(), pago.getId()),
				// Header que pondría un productor con Jackson2JsonMessageConverter: se ignora.
				"com.nahumotos.reservas_service.eventos.ReservaRechazada");

		await().atMost(ESPERA).until(() -> estadoDe(pago) == EstadoPago.REEMBOLSADO);
		verify(mercadoPago).reembolsar(ORDER_ID, pago.claveDeReembolso());
	}

	@Test
	void reservaRechazadaRepetida_unSoloReembolso() {
		UUID eventoId = UUID.randomUUID();
		publicar("reserva.rechazada", rechazada(eventoId, pago.getId()), null);
		publicar("reserva.rechazada", rechazada(eventoId, pago.getId()), null);

		esperarQueSeConsumaLaCola("reserva.rechazada");

		verify(mercadoPago, times(1)).reembolsar(eq(ORDER_ID), any());
		assertThat(estadoDe(pago)).isEqualTo(EstadoPago.REEMBOLSADO);
	}

	@Test
	void dosRechazosDistintosDelMismoPago_unSoloReembolso() {
		publicar("reserva.rechazada", rechazada(UUID.randomUUID(), pago.getId()), null);
		await().atMost(ESPERA).until(() -> estadoDe(pago) == EstadoPago.REEMBOLSADO);

		publicar("reserva.rechazada", rechazada(UUID.randomUUID(), pago.getId()), null);
		esperarQueSeConsumaLaCola("reserva.rechazada");

		verify(mercadoPago, times(1)).reembolsar(eq(ORDER_ID), any());
	}

	@Test
	void mercadoPagoCaido_quedaReembolsoPendienteYElReintentoLoCompleta() {
		when(mercadoPago.reembolsar(any(), any()))
				.thenThrow(new ServicioNoDisponibleException("Mercado Pago", "Mercado Pago respondió 500", null))
				.thenReturn(true);

		publicar("reserva.rechazada", rechazada(UUID.randomUUID(), pago.getId()), null);
		verify(mercadoPago, timeout(ESPERA.toMillis())).reembolsar(any(), any());
		assertThat(estadoDe(pago)).isEqualTo(EstadoPago.REEMBOLSO_PENDIENTE);

		assertThat(reintento.reintentar()).isEqualTo(1);

		assertThat(estadoDe(pago)).isEqualTo(EstadoPago.REEMBOLSADO);
		// Misma X-Idempotency-Key en los dos intentos: Mercado Pago no puede devolver dos veces.
		verify(mercadoPago, times(2)).reembolsar(ORDER_ID, pago.claveDeReembolso());
	}

	@Test
	void errorPermanenteDeMercadoPago_quedaReembolsoFallidoYNoSeReintenta() {
		// 400 refund_amount_exceeds: reintentar cada minuto no lo arregla; hay que mirarlo a mano.
		when(mercadoPago.reembolsar(any(), any())).thenThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST,
				"Bad Request", HttpHeaders.EMPTY,
				"{\"errors\":[{\"code\":\"refund_amount_exceeds\"}]}".getBytes(StandardCharsets.UTF_8),
				StandardCharsets.UTF_8));

		publicar("reserva.rechazada", rechazada(UUID.randomUUID(), pago.getId()), null);

		await().atMost(ESPERA).until(() -> estadoDe(pago) == EstadoPago.REEMBOLSO_FALLIDO);
		assertThat(reintento.reintentar()).isZero();
		verify(mercadoPago, times(1)).reembolsar(any(), any());
	}

	@Test
	void conflictoYLaOrderYaEstaReembolsada_quedaReembolsado() {
		when(mercadoPago.reembolsar(any(), any())).thenReturn(false);
		when(mercadoPago.obtenerOrder(ORDER_ID)).thenReturn(Optional.of(
				new OrderMp(ORDER_ID, "refunded", "refunded", pago.getId().toString(), new BigDecimal("25000.00"), null)));

		publicar("reserva.rechazada", rechazada(UUID.randomUUID(), pago.getId()), null);

		await().atMost(ESPERA).until(() -> estadoDe(pago) == EstadoPago.REEMBOLSADO);
	}

	@Test
	void conflictoYLaOrderTodaviaNoFigura_reembolsada_siguePendiente() {
		// 409 "order_refund_already_in_process": hasta que la order no diga refunded, no lo damos por hecho.
		when(mercadoPago.reembolsar(any(), any())).thenReturn(false);
		when(mercadoPago.obtenerOrder(ORDER_ID)).thenReturn(Optional.of(
				new OrderMp(ORDER_ID, "processed", "accredited", pago.getId().toString(), new BigDecimal("25000.00"), null)));

		publicar("reserva.rechazada", rechazada(UUID.randomUUID(), pago.getId()), null);

		verify(mercadoPago, timeout(ESPERA.toMillis())).obtenerOrder(ORDER_ID);
		assertThat(estadoDe(pago)).isEqualTo(EstadoPago.REEMBOLSO_PENDIENTE);
	}

	@Test
	void reservaCancelada_reembolsaElPagoAprobadoDeEsaReserva() {
		publicar("reserva.cancelada", cancelada(UUID.randomUUID(), 7L), null);

		await().atMost(ESPERA).until(() -> estadoDe(pago) == EstadoPago.REEMBOLSADO);
		verify(mercadoPago).reembolsar(ORDER_ID, pago.claveDeReembolso());
	}

	@Test
	void reservaCanceladaSinPagoAprobado_noLlamaAMercadoPago() {
		publicar("reserva.cancelada", cancelada(UUID.randomUUID(), 99L), null);

		esperarQueSeConsumaLaCola("reserva.cancelada");

		// El único reembolso es el del pago marcador.
		verify(mercadoPago, times(1)).reembolsar(any(), any());
		verify(mercadoPago).reembolsar(eq("ORDMARCADOR"), any());
		assertThat(estadoDe(pago)).isEqualTo(EstadoPago.APROBADO);
	}

	@Test
	void mensajeIlegible_terminaEnLaDlqSinTrabarLaCola() {
		publicar("reserva.rechazada", "{esto no es json", null);

		// Tras los reintentos (1 s + 2 s + 4 s) el mensaje se rechaza y RabbitMQ lo pasa a la DLQ.
		Message muerto = rabbit.receive(RabbitConfig.COLA_RESERVA_RECHAZADA + ".dlq", Duration.ofSeconds(30).toMillis());
		assertThat(muerto).isNotNull();
		assertThat(new String(muerto.getBody(), StandardCharsets.UTF_8)).isEqualTo("{esto no es json");

		// La cola sigue funcionando para los mensajes siguientes.
		publicar("reserva.rechazada", rechazada(UUID.randomUUID(), pago.getId()), null);
		await().atMost(ESPERA).until(() -> estadoDe(pago) == EstadoPago.REEMBOLSADO);
	}

	/**
	 * Los listeners de cada cola procesan en orden con un solo consumidor: cuando el pago
	 * "marcador", publicado al final, queda reembolsado, los mensajes anteriores ya se procesaron.
	 */
	private void esperarQueSeConsumaLaCola(String routingKey) {
		Pago marcador = pagoAprobado(500L, "ORDMARCADOR");
		String evento = routingKey.equals("reserva.rechazada")
				? rechazada(UUID.randomUUID(), marcador.getId())
				: cancelada(UUID.randomUUID(), 500L);
		publicar(routingKey, evento, null);
		await().atMost(ESPERA).until(() -> estadoDe(marcador) == EstadoPago.REEMBOLSADO);
	}

	private Pago pagoAprobado(Long reservaId, String orderId) {
		Pago nuevo = new Pago(reservaId, new BigDecimal("25000.00"), UUID.randomUUID(), AHORA);
		nuevo.asociarOrder(orderId, "https://www.mercadopago.com.ar/checkout/v1/redirect?order_id=" + orderId, AHORA);
		nuevo.registrarEstadoDeOrder(EstadoPago.APROBADO, AHORA);
		return pagoRepository.save(nuevo);
	}

	private EstadoPago estadoDe(Pago unPago) {
		return pagoRepository.findById(unPago.getId()).orElseThrow().getEstado();
	}

	/** Como lo publica reservas-service; incluye un campo que este servicio no conoce. */
	private static String rechazada(UUID eventoId, Long pagoId) {
		return """
				{"eventoId": "%s", "tipo": "reserva.rechazada", "ocurridoEn": "2030-06-10T15:05:00Z",
				 "reservaId": 7, "pagoId": %d, "motivo": "La reserva venció antes de que se aprobara el pago",
				 "campoNuevo": "se ignora"}
				""".formatted(eventoId, pagoId);
	}

	private static String cancelada(UUID eventoId, Long reservaId) {
		return """
				{"eventoId": "%s", "tipo": "reserva.cancelada", "ocurridoEn": "2030-06-10T15:05:00Z", "reservaId": %d}
				""".formatted(eventoId, reservaId);
	}

	private void publicar(String routingKey, String json, String typeIdAjeno) {
		MessageProperties propiedades = new MessageProperties();
		propiedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
		if (typeIdAjeno != null) {
			propiedades.setHeader("__TypeId__", typeIdAjeno);
		}
		rabbit.send(RabbitConfig.EXCHANGE, routingKey, new Message(json.getBytes(StandardCharsets.UTF_8), propiedades));
	}

}
