package com.nahumotos.reservas_service.reserva;

/**
 * Valores permitidos por el CHECK de la columna {@code reserva.estado}. PENDIENTE_PAGO y
 * CONFIRMADA ocupan el turno (son los del WHERE de ux_reserva_turno_activo).
 */
public enum EstadoReserva {

	PENDIENTE_PAGO,
	CONFIRMADA,
	CANCELADA,
	EXPIRADA

}
