package com.nahumotos.reservas_service.error;

/**
 * Un servicio del que dependemos (hoy, canchas-service) no respondió, tardó demasiado
 * o devolvió un 5xx. {@link GlobalExceptionHandler} la traduce a 503.
 */
public class ServicioNoDisponibleException extends RuntimeException {

	public ServicioNoDisponibleException(String mensaje, Throwable causa) {
		super(mensaje, causa);
	}

}
