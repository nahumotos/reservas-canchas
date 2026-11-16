package com.nahumotos.pagos_service.pago;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Reserva a pagar. El monto sale de la reserva, no lo manda el cliente.")
public record CrearPagoRequest(@NotNull @Positive @Schema(example = "1") Long reservaId) {
}
