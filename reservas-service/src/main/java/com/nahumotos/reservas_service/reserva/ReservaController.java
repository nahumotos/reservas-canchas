package com.nahumotos.reservas_service.reserva;

import java.net.URI;
import java.util.List;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
@RequestMapping("/api/reservas")
@Tag(name = "Reservas", description = "Alta, consulta, confirmación y cancelación de reservas")
public class ReservaController {

	private final ReservaService reservaService;

	public ReservaController(ReservaService reservaService) {
		this.reservaService = reservaService;
	}

	@PostMapping
	@Operation(summary = "Reservar un turno",
			description = "Crea la reserva en PENDIENTE_PAGO; si no se confirma antes de venceEn, expira y el turno se libera.")
	@ApiResponse(responseCode = "201", description = "Reserva creada",
			headers = @Header(name = "Location", description = "URL de la reserva creada"))
	@ApiResponse(responseCode = "400", description = "Campos inválidos, turno que no empieza en punto, fuera de horario o en el pasado",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "La cancha no existe",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "El turno ya está reservado o la cancha no está activa",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "503", description = "canchas-service no responde",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public ResponseEntity<ReservaResponse> crear(@Valid @RequestBody CrearReservaRequest request) {
		ReservaResponse reserva = reservaService.crear(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(reserva.id())
				.toUri();
		return ResponseEntity.created(location).body(reserva);
	}

	@GetMapping("/{id}")
	@Operation(summary = "Obtener una reserva")
	@ApiResponse(responseCode = "200", description = "Reserva encontrada")
	@ApiResponse(responseCode = "404", description = "La reserva no existe",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public ReservaResponse obtener(@Parameter(description = "Id de la reserva", example = "1") @PathVariable Long id) {
		return reservaService.obtener(id);
	}

	@PostMapping("/{id}/confirmar")
	@Operation(summary = "Confirmar una reserva (simula el pago aprobado)",
			description = "Pasa de PENDIENTE_PAGO a CONFIRMADA si todavía no venció. Confirmar una reserva ya "
					+ "confirmada no hace nada. Lo va a llamar pagos-service cuando exista.")
	@ApiResponse(responseCode = "200", description = "Reserva confirmada")
	@ApiResponse(responseCode = "404", description = "La reserva no existe",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "La reserva venció o está cancelada",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public ReservaResponse confirmar(@Parameter(description = "Id de la reserva", example = "1") @PathVariable Long id) {
		return reservaService.confirmar(id);
	}

	@PostMapping("/{id}/cancelar")
	@Operation(summary = "Cancelar una reserva",
			description = "Pasa a CANCELADA y libera el turno. Cancelar una reserva ya cancelada no hace nada.")
	@ApiResponse(responseCode = "200", description = "Reserva cancelada")
	@ApiResponse(responseCode = "404", description = "La reserva no existe",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "La reserva ya expiró",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public ReservaResponse cancelar(@Parameter(description = "Id de la reserva", example = "1") @PathVariable Long id) {
		return reservaService.cancelar(id);
	}

	@GetMapping
	@Operation(summary = "Listar las reservas de un cliente",
			description = "Busca por email sin distinguir mayúsculas. Ordena de la fecha más lejana a la más cercana.")
	@ApiResponse(responseCode = "200", description = "Reservas del cliente (puede ser una lista vacía)")
	@ApiResponse(responseCode = "400", description = "Falta el parámetro clienteEmail",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public List<ReservaResponse> listarPorCliente(
			@Parameter(description = "Email del cliente", example = "ana@mail.com") @RequestParam String clienteEmail) {
		return reservaService.listarPorCliente(clienteEmail);
	}

}
