package com.nahumotos.canchas_service.club;

import java.time.LocalTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Club")
public record ClubResponse(
		@Schema(example = "1") Long id,
		@Schema(example = "Club Atlético Palermo") String nombre,
		@Schema(example = "Av. Santa Fe 1234, CABA") String direccion,
		@Schema(example = "08:00:00") LocalTime horaApertura,
		@Schema(example = "23:00:00") LocalTime horaCierre) {

	static ClubResponse desde(Club club) {
		return new ClubResponse(club.getId(), club.getNombre(), club.getDireccion(),
				club.getHoraApertura(), club.getHoraCierre());
	}

}
