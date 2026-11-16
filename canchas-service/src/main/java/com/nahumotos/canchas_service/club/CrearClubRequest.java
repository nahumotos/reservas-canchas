package com.nahumotos.canchas_service.club;

import java.time.LocalTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos para dar de alta un club")
public record CrearClubRequest(

		@Schema(description = "Nombre del club", example = "Club Atlético Palermo")
		@NotBlank @Size(max = 100)
		String nombre,

		@Schema(description = "Dirección del club", example = "Av. Santa Fe 1234, CABA")
		@NotBlank @Size(max = 200)
		String direccion,

		@Schema(description = "Hora de apertura (HH:mm)", example = "08:00")
		@NotNull
		LocalTime horaApertura,

		@Schema(description = "Hora de cierre (HH:mm). Puede ser menor que la apertura si el club cierra después de medianoche", example = "23:00")
		@NotNull
		LocalTime horaCierre) {
}
