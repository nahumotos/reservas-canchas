package com.nahumotos.pagos_service.reservas;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

/** Propiedades {@code reservas-service.*}: dónde está reservas-service y cuánto esperarlo. */
@Validated
@ConfigurationProperties("reservas-service")
public record ReservasServiceProperties(
		@NotNull URI url,
		@NotNull Duration timeoutConexion,
		@NotNull Duration timeoutLectura) {
}
