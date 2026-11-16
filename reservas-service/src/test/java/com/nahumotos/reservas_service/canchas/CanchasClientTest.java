package com.nahumotos.reservas_service.canchas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.time.LocalTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.nahumotos.reservas_service.error.RecursoNoEncontradoException;
import com.nahumotos.reservas_service.error.ServicioNoDisponibleException;

class CanchasClientTest {

	private static final String BASE = "http://canchas.test";

	private MockRestServiceServer servidor;
	private CanchasClient cliente;

	@BeforeEach
	void armarCliente() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
		servidor = MockRestServiceServer.bindTo(builder).build();
		cliente = new CanchasClient(builder.build());
	}

	@Test
	void obtenerCancha_200_mapeaCamposEIgnoraDesconocidos() {
		servidor.expect(requestTo(BASE + "/api/canchas/10")).andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("""
						{"id":10,"clubId":1,"nombre":"Cancha 1","deporte":"FUTBOL5",
						 "precioHora":25000.00,"activa":true,"campoNuevo":"x"}
						""", MediaType.APPLICATION_JSON));

		assertThat(cliente.obtenerCancha(10L))
				.isEqualTo(new CanchaRemota(10L, 1L, "Cancha 1", "FUTBOL5", new BigDecimal("25000.00"), true));
	}

	@Test
	void obtenerClub_200_mapeaHorario() {
		servidor.expect(requestTo(BASE + "/api/clubes/1")).andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("""
						{"id":1,"nombre":"Club","direccion":"Av. Siempre Viva 742",
						 "horaApertura":"08:00:00","horaCierre":"23:00:00"}
						""", MediaType.APPLICATION_JSON));

		assertThat(cliente.obtenerClub(1L))
				.isEqualTo(new ClubRemoto(1L, "Club", LocalTime.of(8, 0), LocalTime.of(23, 0)));
	}

	@Test
	void obtenerCancha_404_lanzaRecursoNoEncontrado() {
		servidor.expect(requestTo(BASE + "/api/canchas/99")).andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThatThrownBy(() -> cliente.obtenerCancha(99L))
				.isInstanceOf(RecursoNoEncontradoException.class)
				.hasMessage("No existe la cancha con id 99");
	}

	@Test
	void obtenerClub_404_lanzaRecursoNoEncontrado() {
		servidor.expect(requestTo(BASE + "/api/clubes/7")).andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThatThrownBy(() -> cliente.obtenerClub(7L))
				.isInstanceOf(RecursoNoEncontradoException.class)
				.hasMessage("No existe el club con id 7");
	}

	@Test
	void obtenerCancha_500_lanzaServicioNoDisponible() {
		servidor.expect(requestTo(BASE + "/api/canchas/10")).andRespond(withServerError());

		assertThatThrownBy(() -> cliente.obtenerCancha(10L)).isInstanceOf(ServicioNoDisponibleException.class);
	}

	@Test
	void obtenerCancha_sinConexion_lanzaServicioNoDisponible() {
		servidor.expect(requestTo(BASE + "/api/canchas/10")).andRespond(withException(new ConnectException()));

		assertThatThrownBy(() -> cliente.obtenerCancha(10L)).isInstanceOf(ServicioNoDisponibleException.class);
	}

	@Test
	void obtenerCancha_timeout_lanzaServicioNoDisponible() {
		servidor.expect(requestTo(BASE + "/api/canchas/10"))
				.andRespond(withException(new HttpTimeoutException("timeout")));

		assertThatThrownBy(() -> cliente.obtenerCancha(10L)).isInstanceOf(ServicioNoDisponibleException.class);
	}

}
