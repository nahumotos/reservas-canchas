package com.nahumotos.reservas_service.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * El pago {@code pagoId} llegó pero la reserva no se pudo confirmar (venció, se canceló…):
 * pagos-service lo reembolsa.
 */
public record ReservaRechazada(UUID eventoId, String tipo, Instant ocurridoEn, Long reservaId, Long pagoId,
		String motivo) implements Evento {

	public static final String TIPO = "reserva.rechazada";

	public static ReservaRechazada de(Long reservaId, Long pagoId, String motivo, Instant ahora) {
		return new ReservaRechazada(UUID.randomUUID(), TIPO, ahora, reservaId, pagoId, motivo);
	}

}
