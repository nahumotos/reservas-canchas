package com.nahumotos.reservas_service.error;

/** Una reserva, cancha o club que no existe. {@link GlobalExceptionHandler} la traduce a 404. */
public class RecursoNoEncontradoException extends RuntimeException {

	public RecursoNoEncontradoException(String mensaje) {
		super(mensaje);
	}

}
