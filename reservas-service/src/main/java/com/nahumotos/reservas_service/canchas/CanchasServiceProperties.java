package com.nahumotos.reservas_service.canchas;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

/** Propiedades {@code canchas-service.*}: dónde está canchas-service y cuánto esperarlo. */
@Validated
@ConfigurationProperties("canchas-service")
public record CanchasServiceProperties(
		@NotNull URI url,
		@NotNull Duration timeoutConexion,
		@NotNull Duration timeoutLectura) {
}
