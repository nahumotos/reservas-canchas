package com.nahumotos.reservas_service.config;

import java.time.Duration;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Propiedades {@code reservas.*}.
 *
 * @param zonaHoraria            zona de los clubes; define qué es "ahora" para los turnos (la JVM corre en UTC)
 * @param vencimientoPago        cuánto tiene el cliente para pagar antes de que la reserva expire
 * @param anticipacionMaximaDias hasta cuántos días después de hoy se puede reservar
 * @param cancelacion            reglas para cancelar una reserva ya pagada
 */
@Validated
@ConfigurationProperties("reservas")
public record ReservasProperties(
		@NotNull ZoneId zonaHoraria,
		@NotNull Duration vencimientoPago,
		@Positive int anticipacionMaximaDias,
		@Valid @NotNull Cancelacion cancelacion) {

	/**
	 * @param anticipacionMinima una reserva CONFIRMADA se puede cancelar (y reembolsar) hasta
	 *                           este tiempo antes del inicio del turno
	 */
	public record Cancelacion(@NotNull Duration anticipacionMinima) {
	}

}
