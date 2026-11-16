package com.nahumotos.reservas_service.eventos;

import java.time.Instant;
import java.util.UUID;

/** La reserva quedó confirmada con el pago {@code pagoId}. Todavía no la consume nadie. */
public record ReservaConfirmada(UUID eventoId, String tipo, Instant ocurridoEn, Long reservaId, Long pagoId)
		implements Evento {

	public static final String TIPO = "reserva.confirmada";

	public static ReservaConfirmada de(Long reservaId, Long pagoId, Instant ahora) {
		return new ReservaConfirmada(UUID.randomUUID(), TIPO, ahora, reservaId, pagoId);
	}

}
