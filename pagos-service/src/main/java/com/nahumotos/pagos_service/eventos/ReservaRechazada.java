package com.nahumotos.pagos_service.eventos;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * reservas-service no pudo confirmar la reserva del pago {@code pagoId} (venció, se canceló…):
 * hay que devolver la plata. Ignora campos desconocidos para que reservas-service pueda
 * agregar datos sin romper este consumidor.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReservaRechazada(UUID eventoId, String tipo, Instant ocurridoEn, Long reservaId, Long pagoId,
		String motivo) implements Evento {

	public static final String TIPO = "reserva.rechazada";

}
