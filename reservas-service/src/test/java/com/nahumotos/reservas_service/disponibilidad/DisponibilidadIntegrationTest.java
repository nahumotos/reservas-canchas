package com.nahumotos.reservas_service.disponibilidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.nahumotos.reservas_service.PruebaDeIntegracion;
import com.nahumotos.reservas_service.error.RecursoNoEncontradoException;
import com.nahumotos.reservas_service.reserva.Reserva;

class DisponibilidadIntegrationTest extends PruebaDeIntegracion {

	@Test
	void disponibilidad_diaFuturo_excluyeSoloTurnosOcupados() throws Exception {
		reservar(LocalTime.of(18, 0));
		Long confirmada = reservar(LocalTime.of(20, 0));
		Long cancelada = reservar(LocalTime.of(21, 0));
		jdbc.update("update reserva set estado = 'CONFIRMADA' where id = ?", confirmada);
		jdbc.update("update reserva set estado = 'CANCELADA' where id = ?", cancelada);

		String cuerpo = consultar("2030-06-11")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.canchaId").value(10))
				.andExpect(jsonPath("$.fecha").value("2030-06-11"))
				.andExpect(jsonPath("$.turnos", hasSize(13)))
				.andExpect(jsonPath("$.turnos[0].horaInicio").value("08:00:00"))
				.andExpect(jsonPath("$.turnos[0].horaFin").value("09:00:00"))
				.andReturn().getResponse().getContentAsString();

		List<String> inicios = JsonPath.read(cuerpo, "$.turnos[*].horaInicio");
		assertThat(inicios).doesNotContain("18:00:00", "20:00:00").contains("21:00:00");
	}

	@Test
	void disponibilidad_hoy_excluyeTurnosQueYaEmpezaron() throws Exception {
		// Son las 12:00 del 10/06: el primer turno libre es el de las 13:00.
		consultar("2030-06-10")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.turnos", hasSize(10)))
				.andExpect(jsonPath("$.turnos[0].horaInicio").value("13:00:00"));
	}

	@Test
	void disponibilidad_clubQueCierraALaMedianoche_incluyeTurnoDe23a00() throws Exception {
		when(canchasClient.obtenerClub(CLUB_ID)).thenReturn(club(LocalTime.of(8, 0), LocalTime.MIDNIGHT));

		consultar("2030-06-11")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.turnos", hasSize(16)))
				.andExpect(jsonPath("$.turnos[15].horaInicio").value("23:00:00"))
				.andExpect(jsonPath("$.turnos[15].horaFin").value("00:00:00"));
	}

	@Test
	void disponibilidad_fechaPasada_listaVacia() throws Exception {
		consultar("2030-06-09").andExpect(status().isOk()).andExpect(jsonPath("$.turnos", empty()));
	}

	@Test
	void disponibilidad_canchaInactiva_listaVacia() throws Exception {
		when(canchasClient.obtenerCancha(CANCHA_ID)).thenReturn(cancha(false));

		consultar("2030-06-11").andExpect(status().isOk()).andExpect(jsonPath("$.turnos", empty()));
	}

	@Test
	void disponibilidad_fechaMalFormada_400() throws Exception {
		consultar("11-06-2030").andExpect(status().isBadRequest());
	}

	@Test
	void disponibilidad_canchaInexistente_404() throws Exception {
		when(canchasClient.obtenerCancha(CANCHA_ID))
				.thenThrow(new RecursoNoEncontradoException("No existe la cancha con id 10"));

		consultar("2030-06-11")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("No existe la cancha con id 10"));
	}

	@Test
	void disponibilidad_masAllaDeLaAnticipacionMaxima_listaVacia() throws Exception {
		// Hoy es el 10/06/2030: el 09/08 está dentro de los 60 días y el 10/08 ya no.
		consultar("2030-08-10").andExpect(status().isOk()).andExpect(jsonPath("$.turnos", empty()));
		consultar("2030-08-09").andExpect(status().isOk()).andExpect(jsonPath("$.turnos", hasSize(15)));
	}

	@Test
	void disponibilidad_pendienteVencida_noOcupaElTurno() throws Exception {
		reservar(LocalTime.of(18, 0)); // PENDIENTE_PAGO, vence a las 12:10
		reloj.avanzar(Duration.ofMinutes(10)); // vencida, pero la tarea programada todavía no la marcó

		String cuerpo = consultar("2030-06-11")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.turnos", hasSize(15)))
				.andReturn().getResponse().getContentAsString();

		List<String> inicios = JsonPath.read(cuerpo, "$.turnos[*].horaInicio");
		assertThat(inicios).contains("18:00:00");
	}

	private ResultActions consultar(String fecha) throws Exception {
		return mockMvc.perform(get("/api/canchas/{canchaId}/disponibilidad", CANCHA_ID).param("fecha", fecha));
	}

	private Long reservar(LocalTime horaInicio) {
		Instant ahora = reloj.instant();
		return reservaRepository.saveAndFlush(new Reserva(CANCHA_ID, CLUB_ID, MANIANA, horaInicio, "Ana",
				"ana@mail.com", new BigDecimal("25000.00"), ahora, ahora.plus(Duration.ofMinutes(10)))).getId();
	}

}
