package com.nahumotos.reservas_service.reserva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.nahumotos.reservas_service.PruebaDeIntegracion;
import com.nahumotos.reservas_service.canchas.CanchaRemota;
import com.nahumotos.reservas_service.error.RecursoNoEncontradoException;
import com.nahumotos.reservas_service.error.ServicioNoDisponibleException;

class ReservaApiIntegrationTest extends PruebaDeIntegracion {

	@Test
	void crear_turnoValido_201ConLocationYPendientePago() throws Exception {
		MvcResult resultado = crear("2030-06-11", "18:00", "ana@mail.com")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.estado").value("PENDIENTE_PAGO"))
				.andExpect(jsonPath("$.canchaId").value(10))
				.andExpect(jsonPath("$.clubId").value(1))
				.andExpect(jsonPath("$.fecha").value("2030-06-11"))
				.andExpect(jsonPath("$.horaInicio").value("18:00:00"))
				.andExpect(jsonPath("$.horaFin").value("19:00:00"))
				.andExpect(jsonPath("$.creadaEn").value("2030-06-10T15:00:00Z"))
				.andExpect(jsonPath("$.venceEn").value("2030-06-10T15:10:00Z"))
				.andReturn();

		Integer id = JsonPath.read(resultado.getResponse().getContentAsString(), "$.id");
		assertThat(resultado.getResponse().getHeader("Location")).endsWith("/api/reservas/" + id);
	}

	@Test
	void crear_turnoFueraDeHorario_400() throws Exception {
		crear("2030-06-11", "07:00", "ana@mail.com")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail", containsString("fuera del horario")));
	}

	@Test
	void crear_turnoQueTerminaDespuesDelCierre_400() throws Exception {
		crear("2030-06-11", "23:00", "ana@mail.com").andExpect(status().isBadRequest());
	}

	@Test
	void crear_turnoDe23a00ConClubAbierto_201() throws Exception {
		when(canchasClient.obtenerClub(CLUB_ID)).thenReturn(club(LocalTime.of(8, 0), LocalTime.MIDNIGHT));

		crear("2030-06-11", "23:00", "ana@mail.com")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.horaInicio").value("23:00:00"))
				.andExpect(jsonPath("$.horaFin").value("00:00:00"));
	}

	@Test
	void crear_horaNoEnPunto_400() throws Exception {
		crear("2030-06-11", "18:30", "ana@mail.com")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail", containsString("en punto")));
	}

	@Test
	void crear_turnoEnElPasado_400() throws Exception {
		crear("2030-06-10", "11:00", "ana@mail.com")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail", containsString("ya empezó o está en el pasado")));
		// Son las 12:00: el turno de las 12:00 ya está en curso.
		crear("2030-06-10", "12:00", "ana@mail.com").andExpect(status().isBadRequest());
	}

	@Test
	void crear_canchaInactiva_409() throws Exception {
		when(canchasClient.obtenerCancha(CANCHA_ID)).thenReturn(cancha(false));

		crear("2030-06-11", "18:00", "ana@mail.com")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("La cancha 10 no está activa"));
	}

	@Test
	void crear_canchaInexistente_404() throws Exception {
		when(canchasClient.obtenerCancha(CANCHA_ID))
				.thenThrow(new RecursoNoEncontradoException("No existe la cancha con id 10"));

		crear("2030-06-11", "18:00", "ana@mail.com")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("No existe la cancha con id 10"));
	}

	@Test
	void crear_canchasServiceCaido_503() throws Exception {
		when(canchasClient.obtenerCancha(CANCHA_ID))
				.thenThrow(new ServicioNoDisponibleException("canchas-service no está disponible", null));

		crear("2030-06-11", "18:00", "ana@mail.com")
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.title").value("Servicio no disponible"));
	}

	@Test
	void crear_turnoYaReservado_409() throws Exception {
		crear("2030-06-11", "18:00", "ana@mail.com").andExpect(status().isCreated());

		crear("2030-06-11", "18:00", "beto@mail.com")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("El turno del 2030-06-11 a las 18:00 ya está reservado"));
	}

	@Test
	void crear_bodyVacio_400ConErroresPorCampo() throws Exception {
		MvcResult resultado = mockMvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andReturn();

		List<String> campos = JsonPath.read(resultado.getResponse().getContentAsString(), "$.errores[*].campo");
		assertThat(campos).containsExactly("canchaId", "clienteEmail", "clienteNombre", "fecha", "horaInicio");
	}

	@Test
	void obtener_existente_200() throws Exception {
		Integer id = JsonPath.read(crear("2030-06-11", "18:00", "ana@mail.com").andReturn().getResponse().getContentAsString(), "$.id");

		mockMvc.perform(get("/api/reservas/{id}", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.clienteNombre").value("Ana"));
	}

	@Test
	void obtener_inexistente_404() throws Exception {
		mockMvc.perform(get("/api/reservas/{id}", 999_999))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("No existe la reserva con id 999999"));
	}

	@Test
	void listarPorCliente_normalizaEmail() throws Exception {
		crear("2030-06-11", "18:00", " Ana@Mail.com ").andExpect(status().isCreated());

		mockMvc.perform(get("/api/reservas").param("clienteEmail", "ANA@mail.com"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasSize(1)))
				.andExpect(jsonPath("$[0].clienteEmail").value("ana@mail.com"));
	}

	@Test
	void listarPorCliente_sinParametro_400() throws Exception {
		mockMvc.perform(get("/api/reservas")).andExpect(status().isBadRequest());
	}

	@Test
	void confirmar_yaNoEsUnEndpoint_404YLaReservaSigueSinConfirmar() throws Exception {
		// La confirmación sólo llega por el evento pago.aprobado (ConfirmacionPorPagoIntegrationTest).
		Integer id = crearYObtenerId();

		mockMvc.perform(post("/api/reservas/{id}/confirmar", id)).andExpect(status().isNotFound());

		mockMvc.perform(get("/api/reservas/{id}", id)).andExpect(jsonPath("$.estado").value("PENDIENTE_PAGO"));
	}

	@Test
	void cancelar_200Cancelada_yElTurnoQuedaLibre() throws Exception {
		Integer id = crearYObtenerId();

		mockMvc.perform(post("/api/reservas/{id}/cancelar", id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.estado").value("CANCELADA"));

		crear("2030-06-11", "18:00", "beto@mail.com").andExpect(status().isCreated());
	}

	@Test
	void crear_guardaElPrecioDeLaCancha() throws Exception {
		crear("2030-06-11", "18:00", "ana@mail.com")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.monto").value(25000.0));
	}

	@Test
	void monto_quedaCongeladoAunqueLaCanchaCambieDePrecio() throws Exception {
		Integer anterior = crearYObtenerId();
		when(canchasClient.obtenerCancha(CANCHA_ID)).thenReturn(
				new CanchaRemota(CANCHA_ID, CLUB_ID, "Cancha 1", "FUTBOL5", new BigDecimal("30000.00"), true));

		crear("2030-06-11", "19:00", "beto@mail.com")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.monto").value(30000.0));
		mockMvc.perform(get("/api/reservas/{id}", anterior))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.monto").value(25000.0));
	}

	@Test
	void crear_masAllaDeLaAnticipacionMaxima_400() throws Exception {
		// Hoy es el 10/06/2030 y el máximo es 60 días: el último día reservable es el 09/08/2030.
		crear("2030-08-10", "18:00", "ana@mail.com")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail")
						.value("Solo se puede reservar con hasta 60 días de anticipación (último día: 2030-08-09)"));
		crear("2030-08-09", "18:00", "ana@mail.com").andExpect(status().isCreated());
	}

	@Test
	void crear_pocoAntesDelTurno_venceAlEmpezarElTurno() throws Exception {
		reloj.fijar(LocalDateTime.of(HOY, LocalTime.of(11, 55)));

		// 11:55 + 10 minutos serían las 12:05, pero el turno empieza a las 12:00 (15:00 UTC).
		crear("2030-06-10", "12:00", "ana@mail.com")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.venceEn").value("2030-06-10T15:00:00Z"));
	}

	@Test
	void crear_turnoConPendienteVencidaQueLaTareaNoExpiro_201() throws Exception {
		Integer anterior = crearYObtenerId(); // 18:00, vence a las 12:10
		reloj.avanzar(Duration.ofMinutes(10)); // 12:10: vencida, pero la tarea programada todavía no corrió

		crear("2030-06-11", "18:00", "beto@mail.com").andExpect(status().isCreated());

		assertThat(reservaRepository.findById(anterior.longValue()).orElseThrow().getEstado())
				.isEqualTo(EstadoReserva.EXPIRADA);
	}

	private Integer crearYObtenerId() throws Exception {
		String cuerpo = crear("2030-06-11", "18:00", "ana@mail.com")
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(cuerpo, "$.id");
	}

	private ResultActions crear(String fecha, String horaInicio, String email) throws Exception {
		String cuerpo = """
				{"canchaId": 10, "fecha": "%s", "horaInicio": "%s", "clienteNombre": "Ana", "clienteEmail": "%s"}
				""".formatted(fecha, horaInicio, email);
		return mockMvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content(cuerpo));
	}

}
