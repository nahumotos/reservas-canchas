package com.nahumotos.pagos_service.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.nahumotos.pagos_service.ColaDePrueba;
import com.nahumotos.pagos_service.FirmasDePrueba;
import com.nahumotos.pagos_service.PruebaDeIntegracion;
import com.nahumotos.pagos_service.error.ServicioNoDisponibleException;
import com.nahumotos.pagos_service.eventos.PublicadorDeEventos;
import com.nahumotos.pagos_service.mercadopago.OrderMp;
import com.nahumotos.pagos_service.pago.EstadoPago;
import com.nahumotos.pagos_service.pago.Pago;
import com.nahumotos.pagos_service.pago.PagoRepository;

/**
 * POST /api/pagos/webhooks/mercadopago. Escenario: el pago de la reserva 7 está PENDIENTE con la
 * order {@value #ORDER_ID}; Mercado Pago (simulado) dice en qué estado está esa order.
 */
class WebhookIntegrationTest extends PruebaDeIntegracion {

	private static final String ORDER_ID = "ORD01JQ4S4KYYWFQFM2FF4XRTA1MX";
	private static final String TS = "1781103600000";

	@Autowired
	private PagoRepository pagoRepository;

	@Autowired
	private PublicadorDeEventos publicador;

	@Autowired
	private AmqpAdmin admin;

	@Autowired
	private RabbitTemplate rabbit;

	private ColaDePrueba pagosAprobados;
	private Long pagoId;

	@BeforeEach
	void pagoPendienteConOrder() {
		pagosAprobados = ColaDePrueba.ligadaA("pago.aprobado", admin, rabbit);
		Pago pago = new Pago(7L, new BigDecimal("25000.00"), UUID.randomUUID(), AHORA);
		pago.asociarOrder(ORDER_ID, "https://www.mercadopago.com.ar/checkout/v1/redirect?order_id=" + ORDER_ID, AHORA);
		pagoId = pagoRepository.save(pago).getId();
	}

	@Test
	void orderProcesada_apruebaElPagoYPublicaPagoAprobado() throws Exception {
		mercadoPagoDice("processed", pagoId.toString());

		notificar(ORDER_ID).andExpect(status().isOk());

		assertThat(estadoDelPago()).isEqualTo(EstadoPago.APROBADO);
		assertThat(publicador.publicarPendientes()).isEqualTo(1);
		Message mensaje = pagosAprobados.recibir();
		String cuerpo = new String(mensaje.getBody(), StandardCharsets.UTF_8);
		assertThat(JsonPath.<String>read(cuerpo, "$.tipo")).isEqualTo("pago.aprobado");
		assertThat(JsonPath.<String>read(cuerpo, "$.eventoId")).isEqualTo(mensaje.getMessageProperties().getMessageId());
		assertThat(JsonPath.<Integer>read(cuerpo, "$.pagoId").longValue()).isEqualTo(pagoId);
		assertThat(JsonPath.<Integer>read(cuerpo, "$.reservaId")).isEqualTo(7);
		assertThat(JsonPath.<Double>read(cuerpo, "$.monto")).isEqualTo(25000.00);
		assertThat(JsonPath.<String>read(cuerpo, "$.mpOrderId")).isEqualTo(ORDER_ID);
	}

	@Test
	void notificacionRepetida_unSoloPagoAprobado() throws Exception {
		mercadoPagoDice("processed", pagoId.toString());

		notificar(ORDER_ID).andExpect(status().isOk());
		notificar(ORDER_ID).andExpect(status().isOk());

		assertThat(jdbc.queryForObject("select count(*) from evento_saliente", Integer.class)).isEqualTo(1);
		assertThat(publicador.publicarPendientes()).isEqualTo(1);
		pagosAprobados.recibir();
		assertThat(pagosAprobados.recibirSiHay()).isNull();
	}

	@Test
	void firmaInvalida_401SinConsultarAMercadoPago() throws Exception {
		mockMvc.perform(post("/api/pagos/webhooks/mercadopago")
				.param("data.id", ORDER_ID).param("type", "order")
				.header("x-signature", "ts=" + TS + ",v1=" + "0".repeat(64))
				.header("x-request-id", "req-1"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.title").value("No autorizado"));

		verify(mercadoPago, never()).obtenerOrder(any());
		assertThat(estadoDelPago()).isEqualTo(EstadoPago.PENDIENTE);
	}

	@Test
	void sinHeaderDeFirma_401() throws Exception {
		mockMvc.perform(post("/api/pagos/webhooks/mercadopago").param("data.id", ORDER_ID).param("type", "order"))
				.andExpect(status().isUnauthorized());

		verify(mercadoPago, never()).obtenerOrder(any());
	}

	@Test
	void noSeConfiaEnElBody_mandaElEstadoQueDevuelveMercadoPago() throws Exception {
		// El body dice "processed", pero al consultar la order está "failed".
		mercadoPagoDice("failed", pagoId.toString());

		notificar(ORDER_ID).andExpect(status().isOk());

		assertThat(estadoDelPago()).isEqualTo(EstadoPago.RECHAZADO);
		assertThat(publicador.publicarPendientes()).isZero();
	}

	@Test
	void orderCancelada_pagoExpiradoSinEvento() throws Exception {
		// Checkout Pro marca "canceled" una order que venció sin pagarse.
		mercadoPagoDice("canceled", pagoId.toString());

		notificar(ORDER_ID).andExpect(status().isOk());

		assertThat(estadoDelPago()).isEqualTo(EstadoPago.EXPIRADO);
		assertThat(publicador.publicarPendientes()).isZero();
	}

	@Test
	void orderInexistenteEnMercadoPago_200SinCambios() throws Exception {
		when(mercadoPago.obtenerOrder("ORDINEXISTENTE")).thenReturn(Optional.empty());

		notificar("ORDINEXISTENTE").andExpect(status().isOk());

		assertThat(estadoDelPago()).isEqualTo(EstadoPago.PENDIENTE);
	}

	@Test
	void orderQueNoEsNuestra_200SinCambios() throws Exception {
		// Otra order de la misma cuenta, con un external_reference que no es un pago nuestro.
		when(mercadoPago.obtenerOrder("ORDAJENA")).thenReturn(Optional.of(
				new OrderMp("ORDAJENA", "processed", "accredited", "pedido-123", new BigDecimal("999.00"), null)));

		notificar("ORDAJENA").andExpect(status().isOk());

		assertThat(estadoDelPago()).isEqualTo(EstadoPago.PENDIENTE);
		assertThat(publicador.publicarPendientes()).isZero();
	}

	@Test
	void orderQueApuntaANuestroPagoPeroEsOtra_200SinCambios() throws Exception {
		// external_reference coincide con nuestro pago, pero el pago tiene otra order.
		when(mercadoPago.obtenerOrder("ORDOTRA")).thenReturn(Optional.of(
				new OrderMp("ORDOTRA", "processed", "accredited", pagoId.toString(), new BigDecimal("25000.00"), null)));

		notificar("ORDOTRA").andExpect(status().isOk());

		assertThat(estadoDelPago()).isEqualTo(EstadoPago.PENDIENTE);
	}

	@Test
	void pagoQueNoLlegoAGuardarSuOrder_seReconocePorExternalReference() throws Exception {
		// Mercado Pago creó la order pero el servicio se cayó antes de guardar su id.
		Pago sinOrder = pagoRepository.save(new Pago(8L, new BigDecimal("30000.00"), UUID.randomUUID(), AHORA));
		when(mercadoPago.obtenerOrder("ORDHUERFANA")).thenReturn(Optional.of(new OrderMp("ORDHUERFANA", "processed",
				"accredited", sinOrder.getId().toString(), new BigDecimal("30000.00"), null)));

		notificar("ORDHUERFANA").andExpect(status().isOk());

		Pago actualizado = pagoRepository.findById(sinOrder.getId()).orElseThrow();
		assertThat(actualizado.getMpOrderId()).isEqualTo("ORDHUERFANA");
		assertThat(actualizado.getEstado()).isEqualTo(EstadoPago.APROBADO);
	}

	@Test
	void pagoRechazadoQueDespuesSeProcesa_seApruebaYPublicaPagoAprobado() throws Exception {
		// Nunca se ignora una order cobrada: reservas-service decide si confirma o rechaza.
		cambiarEstado(pagoId, EstadoPago.RECHAZADO);
		mercadoPagoDice("processed", pagoId.toString());

		notificar(ORDER_ID).andExpect(status().isOk());

		assertThat(estadoDelPago()).isEqualTo(EstadoPago.APROBADO);
		assertThat(publicador.publicarPendientes()).isEqualTo(1);
		String cuerpo = new String(pagosAprobados.recibir().getBody(), StandardCharsets.UTF_8);
		assertThat(JsonPath.<Integer>read(cuerpo, "$.pagoId").longValue()).isEqualTo(pagoId);
	}

	@Test
	void pagoExpiradoQueSeProcesaConOtroPagoActivoEnLaReserva_seReembolsaSinPagoAprobado() throws Exception {
		// La reserva 7 ya tiene otro pago en curso (el PENDIENTE del escenario base); el viejo, que
		// había expirado, termina cobrado: no puede quedar APROBADO (ux_pago_reserva_activo) y la
		// plata se devuelve.
		Pago viejo = new Pago(7L, new BigDecimal("25000.00"), UUID.randomUUID(), AHORA);
		viejo.asociarOrder("ORDVIEJA", "https://www.mercadopago.com.ar/checkout/v1/redirect?order_id=ORDVIEJA", AHORA);
		viejo.registrarEstadoDeOrder(EstadoPago.EXPIRADO, AHORA);
		viejo = pagoRepository.save(viejo);
		when(mercadoPago.obtenerOrder("ORDVIEJA")).thenReturn(Optional.of(
				new OrderMp("ORDVIEJA", "processed", "accredited", viejo.getId().toString(), new BigDecimal("25000.00"), null)));
		when(mercadoPago.reembolsar(any(), any())).thenReturn(true);

		notificar("ORDVIEJA").andExpect(status().isOk());

		assertThat(pagoRepository.findById(viejo.getId()).orElseThrow().getEstado()).isEqualTo(EstadoPago.REEMBOLSADO);
		verify(mercadoPago).reembolsar("ORDVIEJA", viejo.claveDeReembolso());
		assertThat(estadoDelPago()).isEqualTo(EstadoPago.PENDIENTE);
		assertThat(jdbc.queryForObject("select count(*) from evento_saliente", Integer.class)).isZero();
	}

	@Test
	void mercadoPagoNoResponde_503ParaQueReintente() throws Exception {
		when(mercadoPago.obtenerOrder(ORDER_ID))
				.thenThrow(new ServicioNoDisponibleException("Mercado Pago", "Mercado Pago no está disponible", null));

		notificar(ORDER_ID).andExpect(status().isServiceUnavailable());

		assertThat(estadoDelPago()).isEqualTo(EstadoPago.PENDIENTE);
	}

	@Test
	void notificacionQueNoEsDeUnaOrder_200SinConsultar() throws Exception {
		mockMvc.perform(post("/api/pagos/webhooks/mercadopago")
				.param("data.id", "123456").param("type", "payment")
				.header("x-signature", FirmasDePrueba.xSignature(TS, "req-1", "123456"))
				.header("x-request-id", "req-1"))
				.andExpect(status().isOk());

		verify(mercadoPago, never()).obtenerOrder(any());
	}

	private void cambiarEstado(Long id, EstadoPago estado) {
		Pago pago = pagoRepository.findById(id).orElseThrow();
		pago.registrarEstadoDeOrder(estado, AHORA);
		pagoRepository.save(pago);
	}

	private void mercadoPagoDice(String status, String externalReference) {
		when(mercadoPago.obtenerOrder(ORDER_ID)).thenReturn(Optional.of(
				new OrderMp(ORDER_ID, status, "accredited", externalReference, new BigDecimal("25000.00"), null)));
	}

	/** Notificación como la del ejemplo de la doc, firmada con el secreto de prueba. */
	private ResultActions notificar(String orderId) throws Exception {
		String requestId = UUID.randomUUID().toString();
		return mockMvc.perform(post("/api/pagos/webhooks/mercadopago")
				.param("data.id", orderId).param("type", "order")
				.header("x-signature", FirmasDePrueba.xSignature(TS, requestId, orderId))
				.header("x-request-id", requestId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{
						  "action": "order.processed",
						  "api_version": "v1",
						  "application_id": "4599991948843755",
						  "date_created": "2030-06-10T15:04:12.456Z",
						  "id": "123456789",
						  "live_mode": false,
						  "type": "order",
						  "user_id": "1245621468",
						  "data": {"id": "%s", "status": "processed", "status_detail": "accredited"}
						}
						""".formatted(orderId)));
	}

	private EstadoPago estadoDelPago() {
		return pagoRepository.findById(pagoId).orElseThrow().getEstado();
	}

}
