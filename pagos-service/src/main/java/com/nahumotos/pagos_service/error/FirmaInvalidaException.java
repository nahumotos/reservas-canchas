package com.nahumotos.pagos_service.error;

/** La notificación no trae una firma x-signature válida: no la mandó Mercado Pago. 401. */
public class FirmaInvalidaException extends RuntimeException {

	public FirmaInvalidaException() {
		super("La firma de la notificación no es válida");
	}

}
