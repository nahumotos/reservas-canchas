package com.nahumotos.reservas_service.disponibilidad;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Disponibilidad", description = "Turnos libres de una cancha")
public class DisponibilidadController {

	private final DisponibilidadService disponibilidadService;

	public DisponibilidadController(DisponibilidadService disponibilidadService) {
		this.disponibilidadService = disponibilidadService;
	}

	@GetMapping("/api/canchas/{canchaId}/disponibilidad")
	@Operation(summary = "Turnos libres de un día",
			description = "Turnos de una hora dentro del horario del club que no están reservados ni empezaron. "
					+ "Una cancha inactiva o una fecha pasada devuelven la lista vacía.")
	@ApiResponse(responseCode = "200", description = "Turnos libres (puede ser una lista vacía)")
	@ApiResponse(responseCode = "400", description = "Falta la fecha o no tiene formato AAAA-MM-DD",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "La cancha no existe",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "503", description = "canchas-service no responde",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public DisponibilidadResponse consultar(
			@Parameter(description = "Id de la cancha", example = "1") @PathVariable Long canchaId,
			// Sin iso = DATE, Spring parsearía con el formato corto del locale (p. ej. 1/12/26).
			@Parameter(description = "Fecha (AAAA-MM-DD), hora local del club", example = "2026-12-01")
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
		return disponibilidadService.consultar(canchaId, fecha);
	}

}
