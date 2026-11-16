package com.nahumotos.pagos_service.error;

/** El pago (o la reserva en reservas-service) no existe: 404. */
public class RecursoNoEncontradoException extends RuntimeException {

	public RecursoNoEncontradoException(String mensaje) {
		super(mensaje);
	}

}
