package com.nahumotos.pagos_service.pago;

/** Estados del pago; coinciden con el CHECK de la columna {@code pago.estado}. */
public enum EstadoPago {

	/** Order creada (o por crear); el cliente todavía no pagó. */
	PENDIENTE,

	/** Mercado Pago acreditó el pago. Genera el evento pago.aprobado. */
	APROBADO,

	/** El pago falló (tarjeta rechazada, fondos insuficientes…). */
	RECHAZADO,

	/** La order venció o se canceló sin pagarse. */
	EXPIRADO,

	/** Hay que devolver la plata y Mercado Pago todavía no lo confirmó; se reintenta. */
	REEMBOLSO_PENDIENTE,

	/**
	 * Mercado Pago rechazó el reembolso con un error que reintentar no arregla (un 4xx que no es
	 * 409 ni 429). No se reintenta solo: hay que revisarlo a mano.
	 */
	REEMBOLSO_FALLIDO,

	/** Plata devuelta. */
	REEMBOLSADO

}
