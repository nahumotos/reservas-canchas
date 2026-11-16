package com.nahumotos.pagos_service.error;

/** El pedido es válido pero choca con el estado actual (de la reserva o del pago): 409. */
public class ConflictoException extends RuntimeException {

	public ConflictoException(String mensaje) {
		super(mensaje);
	}

}
