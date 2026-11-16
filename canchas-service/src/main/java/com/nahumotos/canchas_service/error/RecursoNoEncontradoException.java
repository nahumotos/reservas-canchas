package com.nahumotos.canchas_service.error;

/**
 * Se lanza cuando se pide un recurso (club, cancha) que no existe.
 * {@link GlobalExceptionHandler} la traduce a un 404.
 */
public class RecursoNoEncontradoException extends RuntimeException {

	public RecursoNoEncontradoException(String mensaje) {
		super(mensaje);
	}

}
