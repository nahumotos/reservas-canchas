package com.nahumotos.reservas_service.eventos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;
import com.nahumotos.reservas_service.ColaDePrueba;
import com.nahumotos.reservas_service.PruebaDeIntegracion;

class PublicadorDeEventosIntegrationTest extends PruebaDeIntegracion {

	private static final UUID EVENTO_ID = UUID.fromString("5f0c1d2e-3b4a-4c5d-8e6f-7a8b9c0d1e2f");
	private static final Instant OCURRIDO = Instant.parse("2030-06-10T15:00:00Z");

	@Autowired
	private BandejaDeSalida bandejaDeSalida;

	@Autowired
	private PublicadorDeEventos publicador;

	@Autowired
	private TransactionTemplate transaccion;

	@Autowired
	private AmqpAdmin admin;

	@Autowired
	private RabbitTemplate rabbit;

	private ColaDePrueba confirmadas;

	@BeforeEach
	void escucharReservasConfirmadas() {
		confirmadas = ColaDePrueba.ligadaA("reserva.confirmada", admin, rabbit);
	}

	@Test
	void publicarPendientes_publicaElEventoYLoMarcaEnviado() {
		transaccion.executeWithoutResult(estado -> bandejaDeSalida
				.guardar(new ReservaConfirmada(EVENTO_ID, "reserva.confirmada", OCURRIDO, 3L, 7L)));

		assertThat(publicador.publicarPendientes()).isEqualTo(1);

		Message mensaje = confirmadas.recibir();
		assertThat(mensaje.getMessageProperties().getMessageId()).isEqualTo(EVENTO_ID.toString());
		assertThat(mensaje.getMessageProperties().getContentType()).isEqualTo("application/json");
		// El consumidor infiere el tipo de su propio record: no le mandamos nombres de clases nuestras.
		assertThat(mensaje.getMessageProperties().getHeaders()).doesNotContainKey("__TypeId__");
		String cuerpo = new String(mensaje.getBody(), StandardCharsets.UTF_8);
		assertThat(JsonPath.<String>read(cuerpo, "$.eventoId")).isEqualTo(EVENTO_ID.toString());
		assertThat(JsonPath.<String>read(cuerpo, "$.tipo")).isEqualTo("reserva.confirmada");
		assertThat(JsonPath.<String>read(cuerpo, "$.ocurridoEn")).isEqualTo("2030-06-10T15:00:00Z");
		assertThat(JsonPath.<Integer>read(cuerpo, "$.reservaId")).isEqualTo(3);
		assertThat(JsonPath.<Integer>read(cuerpo, "$.pagoId")).isEqualTo(7);

		Boolean enviado = jdbc.queryForObject("select enviado_en is not null from evento_saliente where id = ?",
				Boolean.class, EVENTO_ID);
		assertThat(enviado).isTrue();
	}

	@Test
	void publicarPendientes_noRepiteLosYaEnviados() {
		transaccion.executeWithoutResult(estado -> bandejaDeSalida
				.guardar(new ReservaConfirmada(EVENTO_ID, "reserva.confirmada", OCURRIDO, 3L, 7L)));
		publicador.publicarPendientes();
		confirmadas.recibir();

		assertThat(publicador.publicarPendientes()).isZero();
		assertThat(confirmadas.recibirSiHay()).isNull();
	}

	@Test
	void reservaRechazadaSinLaColaDePagos_quedaPendienteYSaleCuandoLaCrean() {
		// pagos-service todavía no declaró su cola (o RabbitMQ perdió sus datos): si se diera por
		// enviado, el cliente pagó y nadie le devolvería la plata.
		ColaDePrueba.borrar("reserva.rechazada", admin);
		transaccion.executeWithoutResult(estado -> bandejaDeSalida.guardar(new ReservaRechazada(EVENTO_ID,
				"reserva.rechazada", OCURRIDO, 3L, 7L, "La reserva fue cancelada")));

		assertThat(publicador.publicarPendientes()).isZero();
		Boolean pendiente = jdbc.queryForObject("select enviado_en is null from evento_saliente where id = ?",
				Boolean.class, EVENTO_ID);
		assertThat(pendiente).isTrue();

		ColaDePrueba rechazadas = ColaDePrueba.ligadaA("reserva.rechazada", admin, rabbit);

		assertThat(publicador.publicarPendientes()).isEqualTo(1);
		assertThat(rechazadas.recibir().getMessageProperties().getMessageId()).isEqualTo(EVENTO_ID.toString());
	}

	@Test
	void reservaCanceladaSinLaColaDePagos_quedaPendiente() {
		ColaDePrueba.borrar("reserva.cancelada", admin);
		transaccion.executeWithoutResult(estado -> bandejaDeSalida
				.guardar(new ReservaCancelada(EVENTO_ID, "reserva.cancelada", OCURRIDO, 3L)));

		assertThat(publicador.publicarPendientes()).isZero();
	}

	@Test
	void reservaConfirmadaSinConsumidor_seDaPorEnviadaIgual() {
		// Nadie la consume todavía: se publica sin mandatory para que no se acumule en el outbox.
		ColaDePrueba.borrar("reserva.confirmada", admin);
		transaccion.executeWithoutResult(estado -> bandejaDeSalida
				.guardar(new ReservaConfirmada(EVENTO_ID, "reserva.confirmada", OCURRIDO, 3L, 7L)));

		assertThat(publicador.publicarPendientes()).isEqualTo(1);
	}

	@Test
	void guardar_fueraDeUnaTransaccion_falla() {
		// El evento tiene que guardarse junto con el cambio de estado que lo produce, nunca suelto.
		assertThatThrownBy(() -> bandejaDeSalida
				.guardar(new ReservaConfirmada(EVENTO_ID, "reserva.confirmada", OCURRIDO, 3L, 7L)))
				.isInstanceOf(IllegalTransactionStateException.class);
	}

}
