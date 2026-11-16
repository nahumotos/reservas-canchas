package com.nahumotos.pagos_service.error;

/**
 * Un servicio del que dependemos (reservas-service o Mercado Pago) no respondió, tardó
 * demasiado o devolvió un error transitorio (5xx, 429). {@link GlobalExceptionHandler} la
 * traduce a 503; en el webhook eso hace que Mercado Pago reintente la notificación.
 */
public class ServicioNoDisponibleException extends RuntimeException {

	private final String servicio;

	public ServicioNoDisponibleException(String servicio, String mensaje, Throwable causa) {
		super(mensaje, causa);
		this.servicio = servicio;
	}

	/** Nombre para mostrar al cliente, p. ej. "Mercado Pago". */
	public String getServicio() {
		return servicio;
	}

}
