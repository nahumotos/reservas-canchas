package com.nahumotos.pagos_service.reservas;

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
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.nahumotos.pagos_service.error.RecursoNoEncontradoException;
import com.nahumotos.pagos_service.error.ServicioNoDisponibleException;

class ReservasClientTest {

	private static final String BASE = "http://reservas.test";

	private MockRestServiceServer servidor;
	private ReservasClient cliente;

	@BeforeEach
	void armarCliente() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
		servidor = MockRestServiceServer.bindTo(builder).build();
		cliente = new ReservasClient(builder.build());
	}

	@Test
	void obtenerReserva_200_mapeaLoQueUsaPagosEIgnoraElResto() {
		// Respuesta real de GET /api/reservas/{id} de reservas-service.
		servidor.expect(requestTo(BASE + "/api/reservas/7")).andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("""
						{"id":7,"canchaId":10,"clubId":1,"fecha":"2030-06-11","horaInicio":"18:00:00",
						 "horaFin":"19:00:00","clienteNombre":"Ana","clienteEmail":"ana@mail.com",
						 "monto":25000.00,"estado":"PENDIENTE_PAGO","venceEn":"2030-06-10T15:10:00Z",
						 "creadaEn":"2030-06-10T15:00:00Z"}
						""", MediaType.APPLICATION_JSON));

		assertThat(cliente.obtenerReserva(7L)).isEqualTo(new ReservaRemota(7L, new BigDecimal("25000.00"),
				"PENDIENTE_PAGO", Instant.parse("2030-06-10T15:10:00Z")));
	}

	@Test
	void obtenerReserva_404_lanzaRecursoNoEncontrado() {
		servidor.expect(requestTo(BASE + "/api/reservas/99")).andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThatThrownBy(() -> cliente.obtenerReserva(99L))
				.isInstanceOf(RecursoNoEncontradoException.class)
				.hasMessage("No existe la reserva con id 99");
	}

	@Test
	void obtenerReserva_500_lanzaServicioNoDisponible() {
		servidor.expect(requestTo(BASE + "/api/reservas/7")).andRespond(withServerError());

		assertThatThrownBy(() -> cliente.obtenerReserva(7L))
				.isInstanceOf(ServicioNoDisponibleException.class)
				.extracting(ex -> ((ServicioNoDisponibleException) ex).getServicio())
				.isEqualTo("reservas-service");
	}

	@Test
	void obtenerReserva_sinConexion_lanzaServicioNoDisponible() {
		servidor.expect(requestTo(BASE + "/api/reservas/7")).andRespond(withException(new ConnectException()));

		assertThatThrownBy(() -> cliente.obtenerReserva(7L)).isInstanceOf(ServicioNoDisponibleException.class);
	}

	@Test
	void obtenerReserva_timeout_lanzaServicioNoDisponible() {
		servidor.expect(requestTo(BASE + "/api/reservas/7"))
				.andRespond(withException(new HttpTimeoutException("timeout")));

		assertThatThrownBy(() -> cliente.obtenerReserva(7L)).isInstanceOf(ServicioNoDisponibleException.class);
	}

}
