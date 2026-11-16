package com.nahumotos.reservas_service.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * El cliente canceló una reserva CONFIRMADA con la anticipación suficiente: pagos-service
 * reembolsa el pago aprobado de esa reserva (este servicio no guarda el id del pago).
 */
public record ReservaCancelada(UUID eventoId, String tipo, Instant ocurridoEn, Long reservaId) implements Evento {

	public static final String TIPO = "reserva.cancelada";

	public static ReservaCancelada de(Long reservaId, Instant ahora) {
		return new ReservaCancelada(UUID.randomUUID(), TIPO, ahora, reservaId);
	}

}
