package com.nahumotos.pagos_service.eventos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
import com.nahumotos.pagos_service.ColaDePrueba;
import com.nahumotos.pagos_service.PruebaDeIntegracion;

class PublicadorDeEventosIntegrationTest extends PruebaDeIntegracion {

	private static final UUID EVENTO_ID = UUID.fromString("0b9a3c7e-4a51-4f0e-9a43-6a1c2d3e4f50");

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

	private ColaDePrueba pagosAprobados;

	@BeforeEach
	void escucharPagosAprobados() {
		pagosAprobados = ColaDePrueba.ligadaA("pago.aprobado", admin, rabbit);
	}

	@Test
	void publicarPendientes_publicaElEventoYLoMarcaEnviado() {
		transaccion.executeWithoutResult(estado -> bandejaDeSalida.guardar(new PagoAprobado(EVENTO_ID,
				"pago.aprobado", AHORA, 7L, 3L, new BigDecimal("25000.00"), "ORD01JQ4S4KYYWFQFM2FF4XRTA1MX")));

		assertThat(publicador.publicarPendientes()).isEqualTo(1);

		Message mensaje = pagosAprobados.recibir();
		assertThat(mensaje.getMessageProperties().getMessageId()).isEqualTo(EVENTO_ID.toString());
		assertThat(mensaje.getMessageProperties().getContentType()).isEqualTo("application/json");
		// El consumidor infiere el tipo de su propio record: no le mandamos nombres de clases nuestras.
		assertThat(mensaje.getMessageProperties().getHeaders()).doesNotContainKey("__TypeId__");
		String cuerpo = new String(mensaje.getBody(), StandardCharsets.UTF_8);
		assertThat(JsonPath.<String>read(cuerpo, "$.eventoId")).isEqualTo(EVENTO_ID.toString());
		assertThat(JsonPath.<String>read(cuerpo, "$.tipo")).isEqualTo("pago.aprobado");
		assertThat(JsonPath.<String>read(cuerpo, "$.ocurridoEn")).isEqualTo("2030-06-10T15:00:00Z");
		assertThat(JsonPath.<Integer>read(cuerpo, "$.pagoId")).isEqualTo(7);
		assertThat(JsonPath.<Integer>read(cuerpo, "$.reservaId")).isEqualTo(3);
		assertThat(JsonPath.<Double>read(cuerpo, "$.monto")).isEqualTo(25000.00);
		assertThat(JsonPath.<String>read(cuerpo, "$.mpOrderId")).isEqualTo("ORD01JQ4S4KYYWFQFM2FF4XRTA1MX");

		assertThat(jdbc.queryForObject("select enviado_en is not null from evento_saliente where id = ?",
				Boolean.class, EVENTO_ID)).isTrue();
	}

	@Test
	void publicarPendientes_noRepiteLosYaEnviados() {
		transaccion.executeWithoutResult(estado -> bandejaDeSalida.guardar(new PagoAprobado(EVENTO_ID,
				"pago.aprobado", AHORA, 7L, 3L, new BigDecimal("25000.00"), "ORD1")));
		publicador.publicarPendientes();
		pagosAprobados.recibir();

		assertThat(publicador.publicarPendientes()).isZero();
		assertThat(pagosAprobados.recibirSiHay()).isNull();
	}

	@Test
	void sinLaColaDelConsumidor_elEventoQuedaPendienteYSaleCuandoLaCrean() {
		// reservas-service todavía no declaró su cola (o RabbitMQ perdió sus datos): RabbitMQ
		// confirma igual, pero el mensaje vuelve por mandatory y no puede darse por enviado.
		ColaDePrueba.borrar("pago.aprobado", admin);
		transaccion.executeWithoutResult(estado -> bandejaDeSalida.guardar(new PagoAprobado(EVENTO_ID,
				"pago.aprobado", AHORA, 7L, 3L, new BigDecimal("25000.00"), "ORD1")));

		assertThat(publicador.publicarPendientes()).isZero();
		assertThat(jdbc.queryForObject("select enviado_en is null from evento_saliente where id = ?",
				Boolean.class, EVENTO_ID)).isTrue();

		ColaDePrueba cola = ColaDePrueba.ligadaA("pago.aprobado", admin, rabbit);

		assertThat(publicador.publicarPendientes()).isEqualTo(1);
		assertThat(cola.recibir().getMessageProperties().getMessageId()).isEqualTo(EVENTO_ID.toString());
	}

	@Test
	void guardar_fueraDeUnaTransaccion_falla() {
		// El evento tiene que guardarse junto con el cambio de estado que lo produce, nunca suelto.
		assertThatThrownBy(() -> bandejaDeSalida.guardar(new PagoAprobado(EVENTO_ID, "pago.aprobado", AHORA, 7L, 3L,
				new BigDecimal("25000.00"), "ORD1")))
				.isInstanceOf(IllegalTransactionStateException.class);
	}

}
