package com.nahumotos.reservas_service.reserva;

import java.time.LocalDate;
import java.time.LocalTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos para reservar un turno. Fecha y hora son hora local del club.")
public record CrearReservaRequest(

		@Schema(description = "Id de la cancha en canchas-service", example = "1")
		@NotNull @Positive
		Long canchaId,

		@Schema(description = "Fecha del turno (AAAA-MM-DD)", example = "2026-12-01")
		@NotNull
		LocalDate fecha,

		@Schema(description = "Hora de inicio del turno, en punto (HH:mm). Los turnos duran 1 hora", example = "18:00")
		@NotNull
		LocalTime horaInicio,

		@Schema(description = "Nombre de quien reserva", example = "Ana Pérez")
		@NotBlank @Size(max = 100)
		String clienteNombre,

		@Schema(description = "Email de quien reserva", example = "ana@mail.com")
		@NotBlank @Email @Size(max = 200)
		String clienteEmail) {

	/** Recorta espacios antes de validar, para que @Email no rechace " ana@mail.com ". */
	public CrearReservaRequest {
		clienteNombre = clienteNombre == null ? null : clienteNombre.trim();
		clienteEmail = clienteEmail == null ? null : clienteEmail.trim();
	}

}
