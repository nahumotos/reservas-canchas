package com.nahumotos.pagos_service.reservas;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Lo que pagos-service necesita de una reserva de reservas-service. Lector tolerante: se
 * ignoran los demás campos, y el estado es un String para no rompernos si aparece uno nuevo.
 *
 * @param monto   precio congelado al reservar: es lo que se cobra
 * @param venceEn hasta cuándo se puede pagar
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReservaRemota(Long id, BigDecimal monto, String estado, Instant venceEn) {

	public static final String PENDIENTE_PAGO = "PENDIENTE_PAGO";

	public boolean pendienteDePago() {
		return PENDIENTE_PAGO.equals(estado);
	}

}
