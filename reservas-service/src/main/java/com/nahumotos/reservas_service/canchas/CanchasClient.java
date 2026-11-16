package com.nahumotos.reservas_service.canchas;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.nahumotos.reservas_service.error.RecursoNoEncontradoException;
import com.nahumotos.reservas_service.error.ServicioNoDisponibleException;

/**
 * Lee canchas y clubes de canchas-service y traduce sus fallas a errores de este servicio:
 * un 404 sigue siendo 404; caída, timeout o 5xx pasan a ser 503. Otros 4xx se propagan
 * porque indicarían un bug nuestro (terminan en 500).
 * <p>
 * El bean se arma en {@link CanchasClientConfig}, con la URL y los timeouts configurados.
 */
public class CanchasClient {

	private final RestClient restClient;

	public CanchasClient(RestClient restClient) {
		this.restClient = restClient;
	}

	public CanchaRemota obtenerCancha(Long canchaId) {
		return obtener("/api/canchas/{id}", canchaId, CanchaRemota.class, "No existe la cancha con id " + canchaId);
	}

	public ClubRemoto obtenerClub(Long clubId) {
		return obtener("/api/clubes/{id}", clubId, ClubRemoto.class, "No existe el club con id " + clubId);
	}

	private <T> T obtener(String uri, Long id, Class<T> tipo, String mensajeSiNoExiste) {
		T cuerpo;
		try {
			cuerpo = restClient.get()
					.uri(uri, id)
					.retrieve()
					.onStatus(status -> status.isSameCodeAs(HttpStatus.NOT_FOUND), (request, response) -> {
						throw new RecursoNoEncontradoException(mensajeSiNoExiste);
					})
					.onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
						throw new ServicioNoDisponibleException(
								"canchas-service respondió " + response.getStatusCode().value(), null);
					})
					.body(tipo);
		}
		catch (ResourceAccessException ex) {
			// Conexión rechazada, timeout de conexión o de lectura.
			throw new ServicioNoDisponibleException("canchas-service no está disponible", ex);
		}
		if (cuerpo == null) {
			throw new ServicioNoDisponibleException("canchas-service respondió sin cuerpo", null);
		}
		return cuerpo;
	}

}
