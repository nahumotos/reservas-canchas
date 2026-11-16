package com.nahumotos.pagos_service.pago;

import java.util.Optional;

/**
 * Traduce el {@code status} de una order de Mercado Pago al estado de nuestro pago. Los
 * intermedios (created, processing, action_required) no cambian nada: el pago sigue PENDIENTE
 * hasta un estado final.
 */
public final class EstadosDeOrder {

	private EstadosDeOrder() {
	}

	public static Optional<EstadoPago> estadoDePago(String statusDeOrder) {
		if (statusDeOrder == null) {
			return Optional.empty();
		}
		return switch (statusDeOrder) {
			case "processed" -> Optional.of(EstadoPago.APROBADO);
			case "failed" -> Optional.of(EstadoPago.RECHAZADO);
			// Checkout Pro marca "canceled" una order que venció sin pagarse; "expired" lo usan
			// otros productos de la API de Orders y lo tratamos igual por si aparece.
			case "expired", "canceled" -> Optional.of(EstadoPago.EXPIRADO);
			case "refunded" -> Optional.of(EstadoPago.REEMBOLSADO);
			default -> Optional.empty();
		};
	}

}
