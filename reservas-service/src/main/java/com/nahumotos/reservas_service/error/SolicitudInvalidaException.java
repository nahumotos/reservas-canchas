package com.nahumotos.reservas_service.error;

/**
 * El pedido pasa Bean Validation pero rompe una regla de turnos (no empieza en punto, está
 * fuera del horario del club o en el pasado). {@link GlobalExceptionHandler} la traduce a 400.
 */
public class SolicitudInvalidaException extends RuntimeException {

	public SolicitudInvalidaException(String mensaje) {
		super(mensaje);
	}

}
