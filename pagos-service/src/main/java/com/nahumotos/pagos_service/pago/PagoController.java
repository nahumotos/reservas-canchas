package com.nahumotos.pagos_service.pago;

import java.net.URI;

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
@RequestMapping("/api/pagos")
@Tag(name = "Pagos", description = "Cobro de reservas con Mercado Pago Checkout Pro")
public class PagoController {

	private final PagoService pagoService;

	public PagoController(PagoService pagoService) {
		this.pagoService = pagoService;
	}

	@PostMapping
	@Operation(summary = "Iniciar el pago de una reserva",
			description = "Crea una order de Mercado Pago por el monto de la reserva y devuelve el checkoutUrl adonde "
					+ "mandar al cliente. Si la reserva ya tiene un pago PENDIENTE, devuelve ese mismo (200). "
					+ "La reserva se confirma sola cuando Mercado Pago avisa que el pago se aprobó.")
	@ApiResponse(responseCode = "201", description = "Pago creado",
			headers = @Header(name = "Location", description = "URL del pago creado"))
	@ApiResponse(responseCode = "200", description = "La reserva ya tenía un pago pendiente: se devuelve ese")
	@ApiResponse(responseCode = "400", description = "Falta reservaId o no es válido",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "La reserva no existe",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409",
			description = "La reserva no está pendiente de pago, vence en menos de un minuto o ya tiene un pago aprobado",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "503", description = "reservas-service o Mercado Pago no responden",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public ResponseEntity<PagoResponse> crear(@Valid @RequestBody CrearPagoRequest request) {
		PagoService.PagoCreado resultado = pagoService.crear(request.reservaId());
		if (!resultado.nuevo()) {
			return ResponseEntity.ok(resultado.pago());
		}
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.buildAndExpand(resultado.pago().pagoId())
				.toUri();
		return ResponseEntity.created(location).body(resultado.pago());
	}

	@GetMapping("/{id}")
	@Operation(summary = "Obtener un pago", description = "Sirve para seguir el estado después de pagar en Mercado Pago.")
	@ApiResponse(responseCode = "200", description = "Pago encontrado")
	@ApiResponse(responseCode = "404", description = "El pago no existe",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public PagoResponse obtener(@Parameter(description = "Id del pago", example = "1") @PathVariable Long id) {
		return pagoService.obtener(id);
	}

}
