package com.nahumotos.canchas_service.cancha;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Cancha")
public record CanchaResponse(
		@Schema(example = "1") Long id,
		@Schema(example = "1") Long clubId,
		@Schema(example = "Cancha 1") String nombre,
		@Schema(example = "FUTBOL5") Deporte deporte,
		@Schema(example = "25000.00") BigDecimal precioHora,
		@Schema(example = "true") boolean activa) {

	/** Sólo lee el id del club, así que no inicializa el proxy LAZY. */
	static CanchaResponse desde(Cancha cancha) {
		return new CanchaResponse(cancha.getId(), cancha.getClub().getId(), cancha.getNombre(),
				cancha.getDeporte(), cancha.getPrecioHora(), cancha.isActiva());
	}

}
