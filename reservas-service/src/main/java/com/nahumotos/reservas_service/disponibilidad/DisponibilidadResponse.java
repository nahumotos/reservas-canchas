package com.nahumotos.reservas_service.disponibilidad;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Turnos libres de una cancha en una fecha, en hora local del club")
public record DisponibilidadResponse(
		@Schema(example = "1") Long canchaId,
		@Schema(example = "2026-12-01") LocalDate fecha,
		List<TurnoLibre> turnos) {

	@Schema(description = "Turno de una hora. Para el de las 23:00, horaFin es 00:00")
	public record TurnoLibre(
			@Schema(example = "18:00:00") LocalTime horaInicio,
			@Schema(example = "19:00:00") LocalTime horaFin) {
	}

}
