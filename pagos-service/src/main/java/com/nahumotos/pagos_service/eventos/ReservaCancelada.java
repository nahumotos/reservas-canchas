package com.nahumotos.pagos_service.eventos;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * El cliente canceló una reserva CONFIRMADA con la anticipación suficiente: hay que reembolsar
 * el pago aprobado de esa reserva. reservas-service no conoce el id del pago, por eso el
 * evento sólo trae {@code reservaId}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReservaCancelada(UUID eventoId, String tipo, Instant ocurridoEn, Long reservaId) implements Evento {

	public static final String TIPO = "reserva.cancelada";

}
