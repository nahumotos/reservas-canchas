package com.nahumotos.reservas_service.error;

/**
 * El pedido es válido pero choca con el estado actual: turno ya reservado, cancha inactiva,
 * reserva vencida o cancelada. {@link GlobalExceptionHandler} la traduce a 409.
 */
public class ConflictoException extends RuntimeException {

	public ConflictoException(String mensaje) {
		super(mensaje);
	}

}
