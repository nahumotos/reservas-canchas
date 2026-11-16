package com.nahumotos.pagos_service.reservas;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.nahumotos.pagos_service.error.RecursoNoEncontradoException;
import com.nahumotos.pagos_service.error.ServicioNoDisponibleException;

/**
 * Lee reservas de reservas-service y traduce sus fallas a errores de este servicio: un 404
 * sigue siendo 404; caída, timeout o 5xx pasan a ser 503. Otros 4xx se propagan porque
 * indicarían un bug nuestro (terminan en 500). Mismo criterio que CanchasClient en reservas.
 */
public class ReservasClient {

	private static final String SERVICIO = "reservas-service";

	private final RestClient restClient;

	public ReservasClient(RestClient restClient) {
		this.restClient = restClient;
	}

	public ReservaRemota obtenerReserva(Long reservaId) {
		ReservaRemota reserva;
		try {
			reserva = restClient.get()
					.uri("/api/reservas/{id}", reservaId)
					.retrieve()
					.onStatus(status -> status.isSameCodeAs(HttpStatus.NOT_FOUND), (pedido, respuesta) -> {
						throw new RecursoNoEncontradoException("No existe la reserva con id " + reservaId);
					})
					.onStatus(HttpStatusCode::is5xxServerError, (pedido, respuesta) -> {
						throw new ServicioNoDisponibleException(SERVICIO,
								"reservas-service respondió " + respuesta.getStatusCode().value(), null);
					})
					.body(ReservaRemota.class);
		}
		catch (ResourceAccessException ex) {
			// Conexión rechazada, timeout de conexión o de lectura.
			throw new ServicioNoDisponibleException(SERVICIO, "reservas-service no está disponible", ex);
		}
		if (reserva == null) {
			throw new ServicioNoDisponibleException(SERVICIO, "reservas-service respondió sin cuerpo", null);
		}
		return reserva;
	}

}
