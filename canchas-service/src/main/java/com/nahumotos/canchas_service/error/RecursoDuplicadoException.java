package com.nahumotos.canchas_service.error;

/**
 * Se lanza cuando un alta violaría una regla de unicidad (por ejemplo, dos canchas
 * con el mismo nombre en un club). {@link GlobalExceptionHandler} la traduce a un 409.
 */
public class RecursoDuplicadoException extends RuntimeException {

	public RecursoDuplicadoException(String mensaje) {
		super(mensaje);
	}

}
