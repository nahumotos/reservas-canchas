package com.nahumotos.reservas_service.eventos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * pagos-service avisa que Mercado Pago aprobó el pago de una reserva. Ignora campos
 * desconocidos para que pagos-service pueda agregar datos sin romper este consumidor.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PagoAprobado(UUID eventoId, String tipo, Instant ocurridoEn, Long pagoId, Long reservaId,
		BigDecimal monto, String mpOrderId) implements Evento {

	public static final String TIPO = "pago.aprobado";

}
