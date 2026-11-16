package com.nahumotos.pagos_service.eventos;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.nahumotos.pagos_service.pago.Pago;

/**
 * Mercado Pago aprobó el pago de una reserva. Lo consume reservas-service, que confirma la
 * reserva o, si ya no se puede, contesta con reserva.rechazada para que reembolsemos.
 */
public record PagoAprobado(UUID eventoId, String tipo, Instant ocurridoEn, Long pagoId, Long reservaId,
		BigDecimal monto, String mpOrderId) implements Evento {

	public static final String TIPO = "pago.aprobado";

	public static PagoAprobado de(Pago pago, Instant ahora) {
		return new PagoAprobado(UUID.randomUUID(), TIPO, ahora, pago.getId(), pago.getReservaId(), pago.getMonto(),
				pago.getMpOrderId());
	}

}
