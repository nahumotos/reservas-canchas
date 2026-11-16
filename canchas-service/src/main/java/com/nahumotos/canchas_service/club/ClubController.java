package com.nahumotos.canchas_service.club;

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
@RequestMapping("/api/clubes")
@Tag(name = "Clubes", description = "Alta y consulta de clubes")
public class ClubController {

	private final ClubService clubService;

	public ClubController(ClubService clubService) {
		this.clubService = clubService;
	}

	@PostMapping
	@Operation(summary = "Crear un club", description = "Da de alta un club con su horario de atención.")
	@ApiResponse(responseCode = "201", description = "Club creado",
			headers = @Header(name = "Location", description = "URL del club creado"))
	@ApiResponse(responseCode = "400", description = "Algún campo es inválido",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public ResponseEntity<ClubResponse> crear(@Valid @RequestBody CrearClubRequest request) {
		ClubResponse club = clubService.crear(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(club.id())
				.toUri();
		return ResponseEntity.created(location).body(club);
	}

	@GetMapping
	@Operation(summary = "Listar clubes", description = "Devuelve todos los clubes ordenados por nombre.")
	@ApiResponse(responseCode = "200", description = "Listado de clubes")
	public List<ClubResponse> listar() {
		return clubService.listar();
	}

	@GetMapping("/{id}")
	@Operation(summary = "Obtener un club", description = "Devuelve el detalle de un club. Es la URL que devuelve el header Location al crearlo.")
	@ApiResponse(responseCode = "200", description = "Club encontrado")
	@ApiResponse(responseCode = "404", description = "El club no existe",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public ClubResponse obtener(@Parameter(description = "Id del club", example = "1") @PathVariable Long id) {
		return clubService.obtener(id);
	}

}
