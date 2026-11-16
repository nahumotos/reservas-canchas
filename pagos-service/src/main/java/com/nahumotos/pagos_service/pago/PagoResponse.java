package com.nahumotos.pagos_service.pago;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Pago de una reserva")
public record PagoResponse(
		@Schema(example = "1") Long pagoId,
		@Schema(example = "1") Long reservaId,
		@Schema(description = "Precio de la reserva", example = "25000.00") BigDecimal monto,
		@Schema(example = "PENDIENTE") EstadoPago estado,
		@Schema(description = "Adonde mandar al cliente para pagar (checkout de Mercado Pago)",
				example = "https://www.mercadopago.com.ar/checkout/v1/redirect?order_id=ORD01JQ4S4KYYWFQFM2FF4XRTA1MX")
		String checkoutUrl) {

	static PagoResponse desde(Pago pago) {
		return new PagoResponse(pago.getId(), pago.getReservaId(), pago.getMonto(), pago.getEstado(),
				pago.getCheckoutUrl());
	}

}
