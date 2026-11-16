package com.nahumotos.reservas_service.reserva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.nahumotos.reservas_service.ColaDePrueba;
import com.nahumotos.reservas_service.PruebaDeIntegracion;
import com.nahumotos.reservas_service.eventos.PublicadorDeEventos;

/**
 * Cancelar una reserva CONFIRMADA (ya pagada) se permite hasta 24 h antes del turno y publica
 * reserva.cancelada para que pagos-service devuelva la plata. Hoy es el 10/06/2030 a las 12:00.
 */
class CancelacionIntegrationTest extends PruebaDeIntegracion {

	@Autowired
	private PublicadorDeEventos publicador;

	@Autowired
	private AmqpAdmin admin;

	@Autowired
	private RabbitTemplate rabbit;

	private ColaDePrueba canceladas;

	@BeforeEach
	void escucharCancelaciones() {
		canceladas = ColaDePrueba.ligadaA("reserva.cancelada", admin, rabbit);
	}

	@Test
	void confirmadaConMasDe24hDeAnticipacion_seCancelaYPublicaReservaCancelada() throws Exception {
		Long id = reservaConfirmada("18:00"); // 11/06 18:00: faltan 30 h

		cancelar(id).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADA"));

		assertThat(publicador.publicarPendientes()).isEqualTo(1);
		String cuerpo = new String(canceladas.recibir().getBody(), StandardCharsets.UTF_8);
		assertThat(JsonPath.<String>read(cuerpo, "$.tipo")).isEqualTo("reserva.cancelada");
		assertThat(JsonPath.<Integer>read(cuerpo, "$.reservaId").longValue()).isEqualTo(id);
	}

	@Test
	void confirmadaConJusto24hDeAnticipacion_seCancela() throws Exception {
		Long id = reservaConfirmada("12:00"); // 11/06 12:00: faltan exactamente 24 h

		cancelar(id).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADA"));

		assertThat(eventosDeCancelacion()).isEqualTo(1);
	}

	@Test
	void confirmadaConMenosDe24hDeAnticipacion_409SinCambiosNiEvento() throws Exception {
		Long id = reservaConfirmada("11:00"); // 11/06 11:00: faltan 23 h

		cancelar(id)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail")
						.value("Las reservas confirmadas se pueden cancelar hasta 24 horas antes del turno"));

		assertThat(reservaRepository.findById(id).orElseThrow().getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
		assertThat(eventosDeCancelacion()).isZero();
	}

	@Test
	void pendienteDePago_seCancelaSinEvento() throws Exception {
		// No se cobró nada: no hay que avisarle a pagos-service. Si el pago llega después, se
		// rechaza y se reembolsa (ConfirmacionPorPagoIntegrationTest).
		Long id = reservar("11:00");

		cancelar(id).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADA"));

		assertThat(eventosDeCancelacion()).isZero();
	}

	@Test
	void cancelarDosVecesUnaConfirmada_unSoloEvento() throws Exception {
		Long id = reservaConfirmada("18:00");

		cancelar(id).andExpect(status().isOk());
		cancelar(id).andExpect(status().isOk());

		assertThat(eventosDeCancelacion()).isEqualTo(1);
	}

	private Long reservaConfirmada(String horaInicio) throws Exception {
		Long id = reservar(horaInicio);
		// La confirmación real llega por pago.aprobado; acá sólo importa que esté CONFIRMADA.
		Reserva reserva = reservaRepository.findById(id).orElseThrow();
		reserva.confirmar(reloj.instant());
		reservaRepository.save(reserva);
		return id;
	}

	private Long reservar(String horaInicio) throws Exception {
		String cuerpo = mockMvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content("""
				{"canchaId": 10, "fecha": "2030-06-11", "horaInicio": "%s", "clienteNombre": "Ana", "clienteEmail": "ana@mail.com"}
				""".formatted(horaInicio)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.<Integer>read(cuerpo, "$.id").longValue();
	}

	private ResultActions cancelar(Long id) throws Exception {
		return mockMvc.perform(post("/api/reservas/{id}/cancelar", id));
	}

	private int eventosDeCancelacion() {
		return jdbc.queryForObject("select count(*) from evento_saliente where tipo = 'reserva.cancelada'",
				Integer.class);
	}

}
