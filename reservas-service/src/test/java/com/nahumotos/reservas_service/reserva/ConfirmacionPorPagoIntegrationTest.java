package com.nahumotos.reservas_service.reserva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;
import com.nahumotos.reservas_service.ColaDePrueba;
import com.nahumotos.reservas_service.PruebaDeIntegracion;
import com.nahumotos.reservas_service.eventos.PublicadorDeEventos;
import com.nahumotos.reservas_service.eventos.RabbitConfig;

/**
 * pago.aprobado llega por RabbitMQ (como lo publicaría pagos-service) y la reserva se confirma o
 * se rechaza. Escenario: la reserva del 11/06 a las 18:00 está PENDIENTE_PAGO, cuesta 25000.00 y
 * vence a las 12:10 (hoy, hora argentina).
 */
class ConfirmacionPorPagoIntegrationTest extends PruebaDeIntegracion {

	private static final Duration ESPERA = Duration.ofSeconds(10);
	private static final long PAGO_ID = 42L;

	@Autowired
	private PublicadorDeEventos publicador;

	@Autowired
	private ExpiracionDeReservas expiracion;

	@Autowired
	private AmqpAdmin admin;

	@Autowired
	private RabbitTemplate rabbit;

	private ColaDePrueba confirmadas;
	private ColaDePrueba rechazadas;
	private Long reservaId;

	@BeforeEach
	void reservaPendiente() throws Exception {
		confirmadas = ColaDePrueba.ligadaA("reserva.confirmada", admin, rabbit);
		rechazadas = ColaDePrueba.ligadaA("reserva.rechazada", admin, rabbit);
		reservaId = reservar("18:00");
	}

	@Test
	void pagoAprobado_confirmaLaReservaYPublicaReservaConfirmada() {
		publicarPagoAprobado(UUID.randomUUID(), reservaId, "25000.00");

		await().atMost(ESPERA).until(() -> estadoDe(reservaId) == EstadoReserva.CONFIRMADA);
		assertThat(publicador.publicarPendientes()).isEqualTo(1);
		String cuerpo = cuerpo(confirmadas.recibir());
		assertThat(JsonPath.<String>read(cuerpo, "$.tipo")).isEqualTo("reserva.confirmada");
		assertThat(JsonPath.<Integer>read(cuerpo, "$.reservaId").longValue()).isEqualTo(reservaId);
		assertThat(JsonPath.<Integer>read(cuerpo, "$.pagoId").longValue()).isEqualTo(PAGO_ID);
		assertThat(rechazadas.recibirSiHay()).isNull();
	}

	@Test
	void reservaVencidaAunqueElJobNoCorrio_rechazadaYSigueSinConfirmar() {
		reloj.avanzar(Duration.ofMinutes(10));

		publicarPagoAprobado(UUID.randomUUID(), reservaId, "25000.00");

		String rechazo = esperarRechazo();
		assertThat(JsonPath.<String>read(rechazo, "$.motivo")).contains("venció");
		assertThat(JsonPath.<Integer>read(rechazo, "$.pagoId").longValue()).isEqualTo(PAGO_ID);
		assertThat(JsonPath.<Integer>read(rechazo, "$.reservaId").longValue()).isEqualTo(reservaId);
		assertThat(estadoDe(reservaId)).isEqualTo(EstadoReserva.PENDIENTE_PAGO);
	}

	@Test
	void reservaExpirada_rechazada() {
		reloj.avanzar(Duration.ofMinutes(11));
		expiracion.expirarVencidas();

		publicarPagoAprobado(UUID.randomUUID(), reservaId, "25000.00");

		assertThat(JsonPath.<String>read(esperarRechazo(), "$.motivo")).contains("expiró");
		assertThat(estadoDe(reservaId)).isEqualTo(EstadoReserva.EXPIRADA);
	}

	@Test
	void reservaCancelada_rechazada() throws Exception {
		// Cancelar una PENDIENTE_PAGO sigue permitido; si después llega el pago, se devuelve.
		mockMvc.perform(post("/api/reservas/{id}/cancelar", reservaId)).andExpect(status().isOk());

		publicarPagoAprobado(UUID.randomUUID(), reservaId, "25000.00");

		assertThat(JsonPath.<String>read(esperarRechazo(), "$.motivo")).contains("cancelada");
		assertThat(estadoDe(reservaId)).isEqualTo(EstadoReserva.CANCELADA);
	}

	@Test
	void reservaInexistente_rechazada() {
		publicarPagoAprobado(UUID.randomUUID(), 999_999L, "25000.00");

		assertThat(JsonPath.<String>read(esperarRechazo(), "$.motivo")).contains("no existe");
	}

	@Test
	void montoDistintoAlDeLaReserva_rechazadaSinConfirmar() {
		publicarPagoAprobado(UUID.randomUUID(), reservaId, "100.00");

		assertThat(JsonPath.<String>read(esperarRechazo(), "$.motivo")).contains("monto");
		assertThat(estadoDe(reservaId)).isEqualTo(EstadoReserva.PENDIENTE_PAGO);
	}

	@Test
	void mensajeRepetido_seProcesaUnaSolaVez() throws Exception {
		UUID eventoId = UUID.randomUUID();
		publicarPagoAprobado(eventoId, reservaId, "25000.00");
		publicarPagoAprobado(eventoId, reservaId, "25000.00");
		// La cola se consume en orden con un solo consumidor: cuando el marcador (otra reserva)
		// queda confirmado, los dos anteriores ya se procesaron.
		Long marcador = reservar("19:00");
		publicarPagoAprobado(UUID.randomUUID(), marcador, "25000.00");
		await().atMost(ESPERA).until(() -> estadoDe(marcador) == EstadoReserva.CONFIRMADA);

		assertThat(estadoDe(reservaId)).isEqualTo(EstadoReserva.CONFIRMADA);
		assertThat(jdbc.queryForObject("select count(*) from evento_procesado", Integer.class)).isEqualTo(2);
		assertThat(jdbc.queryForObject("select count(*) from evento_saliente where tipo = 'reserva.confirmada'",
				Integer.class)).isEqualTo(2);
		assertThat(jdbc.queryForObject("select count(*) from evento_saliente where tipo = 'reserva.rechazada'",
				Integer.class)).isZero();
	}

	@Test
	void mensajeIlegible_terminaEnLaDlqSinTrabarLaCola() {
		publicar("{esto no es json", null);

		// Tras los reintentos (1 s + 2 s + 4 s) el mensaje se rechaza y RabbitMQ lo pasa a la DLQ.
		Message muerto = rabbit.receive(RabbitConfig.COLA_PAGO_APROBADO + ".dlq", Duration.ofSeconds(30).toMillis());
		assertThat(muerto).isNotNull();
		assertThat(cuerpo(muerto)).isEqualTo("{esto no es json");

		publicarPagoAprobado(UUID.randomUUID(), reservaId, "25000.00");
		await().atMost(ESPERA).until(() -> estadoDe(reservaId) == EstadoReserva.CONFIRMADA);
	}

	/** Espera el reserva.rechazada en el outbox, lo publica y devuelve el JSON recibido. */
	private String esperarRechazo() {
		await().atMost(ESPERA).until(() -> jdbc.queryForObject(
				"select count(*) from evento_saliente where tipo = 'reserva.rechazada'", Integer.class) == 1);
		assertThat(publicador.publicarPendientes()).isEqualTo(1);
		assertThat(confirmadas.recibirSiHay()).isNull();
		String cuerpo = cuerpo(rechazadas.recibir());
		assertThat(JsonPath.<String>read(cuerpo, "$.tipo")).isEqualTo("reserva.rechazada");
		return cuerpo;
	}

	/** Como lo publica pagos-service; incluye un campo que este servicio no conoce. */
	private void publicarPagoAprobado(UUID eventoId, Long reserva, String monto) {
		publicar("""
				{"eventoId": "%s", "tipo": "pago.aprobado", "ocurridoEn": "2030-06-10T15:04:00Z",
				 "pagoId": %d, "reservaId": %d, "monto": %s, "mpOrderId": "ORD01JQ4S4KYYWFQFM2FF4XRTA1MX",
				 "campoNuevo": "se ignora"}
				""".formatted(eventoId, PAGO_ID, reserva, monto),
				// Header que pondría un productor con Jackson2JsonMessageConverter: se ignora.
				"com.nahumotos.pagos_service.eventos.PagoAprobado");
	}

	private void publicar(String json, String typeIdAjeno) {
		MessageProperties propiedades = new MessageProperties();
		propiedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
		if (typeIdAjeno != null) {
			propiedades.setHeader("__TypeId__", typeIdAjeno);
		}
		rabbit.send(RabbitConfig.EXCHANGE, "pago.aprobado",
				new Message(json.getBytes(StandardCharsets.UTF_8), propiedades));
	}

	private Long reservar(String horaInicio) throws Exception {
		String cuerpo = mockMvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content("""
				{"canchaId": 10, "fecha": "2030-06-11", "horaInicio": "%s", "clienteNombre": "Ana", "clienteEmail": "ana@mail.com"}
				""".formatted(horaInicio)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.<Integer>read(cuerpo, "$.id").longValue();
	}

	private EstadoReserva estadoDe(Long id) {
		return reservaRepository.findById(id).orElseThrow().getEstado();
	}

	private static String cuerpo(Message mensaje) {
		return new String(mensaje.getBody(), StandardCharsets.UTF_8);
	}

}
