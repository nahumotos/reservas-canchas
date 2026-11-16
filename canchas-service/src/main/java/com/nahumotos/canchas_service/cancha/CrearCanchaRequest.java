package com.nahumotos.canchas_service.cancha;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos para agregar una cancha a un club")
public record CrearCanchaRequest(

		@Schema(description = "Nombre de la cancha, único dentro del club", example = "Cancha 1")
		@NotBlank @Size(max = 50)
		String nombre,

		@Schema(description = "Deporte de la cancha", example = "FUTBOL5")
		@NotNull
		Deporte deporte,

		@Schema(description = "Precio por hora", example = "25000.00")
		@NotNull @Positive @Digits(integer = 8, fraction = 2)
		BigDecimal precioHora) {
}
