package com.nahumotos.pagos_service.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

/**
 * Propiedades {@code pagos.*}.
 *
 * @param margenMinimoParaPagar si a la reserva le queda menos que esto para vencer, no se crea
 *                              el pago: el cliente no llegaría a pagar y habría que reembolsar
 */
@Validated
@ConfigurationProperties("pagos")
public record PagosProperties(@NotNull Duration margenMinimoParaPagar) {
}
