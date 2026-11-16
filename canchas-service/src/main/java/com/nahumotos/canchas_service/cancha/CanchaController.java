package com.nahumotos.canchas_service.cancha;

import java.net.URI;
import java.util.List;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
@Tag(name = "Canchas", description = "Alta y consulta de canchas de un club")
public class CanchaController {

	private final CanchaService canchaService;

	public CanchaController(CanchaService canchaService) {
		this.canchaService = canchaService;
	}

	@PostMapping("/clubes/{clubId}/canchas")
	@Operation(summary = "Agregar una cancha a un club",
			description = "Crea una cancha activa en el club indicado. El nombre no puede repetirse dentro del mismo club.")
	@ApiResponse(responseCode = "201", description = "Cancha creada",
			headers = @Header(name = "Location", description = "URL de la cancha creada"))
	@ApiResponse(responseCode = "400", description = "Algún campo es inválido",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "El club no existe",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "Ya existe una cancha con ese nombre en el club",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public ResponseEntity<CanchaResponse> crear(
			@Parameter(description = "Id del club", example = "1") @PathVariable Long clubId,
			@Valid @RequestBody CrearCanchaRequest request) {
		CanchaResponse cancha = canchaService.crear(clubId, request);
		URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
				.path("/api/canchas/{id}")
				.buildAndExpand(cancha.id())
				.toUri();
		return ResponseEntity.created(location).body(cancha);
	}

	@GetMapping("/clubes/{clubId}/canchas")
	@Operation(summary = "Listar las canchas de un club", description = "Devuelve las canchas del club ordenadas por nombre.")
	@ApiResponse(responseCode = "200", description = "Listado de canchas")
	@ApiResponse(responseCode = "404", description = "El club no existe",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public List<CanchaResponse> listarPorClub(
			@Parameter(description = "Id del club", example = "1") @PathVariable Long clubId) {
		return canchaService.listarPorClub(clubId);
	}

	@GetMapping("/canchas/{id}")
	@Operation(summary = "Obtener una cancha", description = "Devuelve el detalle de una cancha.")
	@ApiResponse(responseCode = "200", description = "Cancha encontrada")
	@ApiResponse(responseCode = "404", description = "La cancha no existe",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public CanchaResponse obtener(@Parameter(description = "Id de la cancha", example = "1") @PathVariable Long id) {
		return canchaService.obtener(id);
	}

}
