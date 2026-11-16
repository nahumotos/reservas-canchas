package com.nahumotos.pagos_service.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nahumotos.pagos_service.error.FirmaInvalidaException;
import com.nahumotos.pagos_service.mercadopago.FirmaDeMercadoPago;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/pagos/webhooks")
@Tag(name = "Webhooks", description = "Notificaciones que manda Mercado Pago. No son para llamar a mano.")
public class WebhookController {

	private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

	private final FirmaDeMercadoPago firma;
	private final NotificacionesDeMercadoPago notificaciones;

	public WebhookController(FirmaDeMercadoPago firma, NotificacionesDeMercadoPago notificaciones) {
		this.firma = firma;
		this.notificaciones = notificaciones;
	}

	@PostMapping("/mercadopago")
	@Operation(summary = "Notificación de Mercado Pago (evento Order)",
			description = "La llama Mercado Pago, no un cliente: se configura en Tus integraciones > Webhooks con el "
					+ "evento Order. Se valida la firma x-signature con la firma secreta; el body se ignora y el "
					+ "estado se toma de GET /v1/orders/{id}. Si el pago queda aprobado se publica pago.aprobado.")
	@ApiResponse(responseCode = "200", description = "Notificación procesada o ignorada (order que no es nuestra)")
	@ApiResponse(responseCode = "401", description = "La firma x-signature falta o no es válida",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "503", description = "Mercado Pago no respondió al consultar la order; reintenta solo",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	public ResponseEntity<Void> recibir(
			@Parameter(description = "Id de la order", example = "ORD01JQ4S4KYYWFQFM2FF4XRTA1MX")
			@RequestParam(name = "data.id", required = false) String dataId,
			@Parameter(description = "Tópico de la notificación", example = "order")
			@RequestParam(name = "type", required = false) String tipo,
			@Parameter(description = "Firma de Mercado Pago: ts=...,v1=...")
			@RequestHeader(name = "x-signature", required = false) String xSignature,
			@RequestHeader(name = "x-request-id", required = false) String xRequestId) {
		if (!firma.esValida(xSignature, xRequestId, dataId)) {
			throw new FirmaInvalidaException();
		}
		if (dataId == null || dataId.isBlank() || (tipo != null && !"order".equals(tipo))) {
			// Otro tópico (si alguien lo activa en el panel): respondemos 200 para que no reintente.
			log.info("Notificación de Mercado Pago ignorada (type={})", tipo);
			return ResponseEntity.ok().build();
		}
		notificaciones.procesar(dataId);
		return ResponseEntity.ok().build();
	}

}
