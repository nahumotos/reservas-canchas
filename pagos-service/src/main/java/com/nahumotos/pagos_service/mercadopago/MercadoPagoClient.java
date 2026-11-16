package com.nahumotos.pagos_service.mercadopago;

import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nahumotos.pagos_service.error.ServicioNoDisponibleException;

/**
 * Cliente de la API de Orders de Mercado Pago (Checkout Pro), con RestClient y sin SDK.
 * <p>
 * No hay URLs ni endpoints "sandbox": con un Access Token de prueba la misma API crea orders de
 * prueba y el único {@code checkout_url} lleva al checkout de prueba.
 * <p>
 * Fallas: 5xx, 429, timeout o sin conexión son transitorias y se traducen a
 * {@link ServicioNoDisponibleException}; otros 4xx se propagan porque indican un error nuestro
 * (token inválido, request mal armado) que reintentar no arregla.
 */
public class MercadoPagoClient {

	private static final String SERVICIO = "Mercado Pago";

	private final RestClient restClient;
	private final String accessToken;

	public MercadoPagoClient(RestClient restClient, String accessToken) {
		this.restClient = restClient;
		this.accessToken = accessToken;
	}

	/**
	 * {@code POST /v1/orders}. La clave de idempotencia es la guardada en el pago: si el pedido se
	 * reintenta (timeout, caída), Mercado Pago devuelve la misma order en vez de crear otra.
	 */
	public OrderMp crearOrder(NuevaOrder nueva, UUID claveIdempotencia) {
		OrderMp order = conFallasTraducidas(() -> restClient.post()
				.uri("/v1/orders")
				.headers(headers -> autenticar(headers, claveIdempotencia))
				.contentType(MediaType.APPLICATION_JSON)
				.body(PedidoDeOrder.desde(nueva))
				.retrieve()
				.onStatus(MercadoPagoClient::esTransitorio, (pedido, respuesta) -> {
					throw transitorio(respuesta.getStatusCode());
				})
				.body(OrderMp.class));
		if (order == null) {
			throw new ServicioNoDisponibleException(SERVICIO, "Mercado Pago respondió sin cuerpo al crear la order", null);
		}
		return order;
	}

	/** {@code GET /v1/orders/{id}}; vacío si la order no existe (404). */
	public Optional<OrderMp> obtenerOrder(String orderId) {
		try {
			return Optional.ofNullable(conFallasTraducidas(() -> restClient.get()
					.uri("/v1/orders/{id}", orderId)
					.headers(headers -> headers.setBearerAuth(accessToken))
					.retrieve()
					.onStatus(MercadoPagoClient::esTransitorio, (pedido, respuesta) -> {
						throw transitorio(respuesta.getStatusCode());
					})
					.body(OrderMp.class)));
		}
		catch (HttpClientErrorException.NotFound ex) {
			return Optional.empty();
		}
	}

	/**
	 * {@code POST /v1/orders/{id}/refund} sin body: reembolso total. Devuelve true si Mercado
	 * Pago lo aceptó y false si contestó 409 (ya reembolsada, reembolso en curso o clave ya
	 * usada); en ese caso el que llama tiene que consultar la order para saber cómo quedó.
	 */
	public boolean reembolsar(String orderId, UUID claveIdempotencia) {
		try {
			conFallasTraducidas(() -> restClient.post()
					.uri("/v1/orders/{id}/refund", orderId)
					.headers(headers -> autenticar(headers, claveIdempotencia))
					.contentType(MediaType.APPLICATION_JSON)
					.retrieve()
					.onStatus(MercadoPagoClient::esTransitorio, (pedido, respuesta) -> {
						throw transitorio(respuesta.getStatusCode());
					})
					.toBodilessEntity());
			return true;
		}
		catch (HttpClientErrorException.Conflict ex) {
			return false;
		}
	}

	private void autenticar(HttpHeaders headers, UUID claveIdempotencia) {
		headers.setBearerAuth(accessToken);
		headers.set("X-Idempotency-Key", claveIdempotencia.toString());
	}

	private static <T> T conFallasTraducidas(Supplier<T> llamada) {
		try {
			return llamada.get();
		}
		catch (ResourceAccessException ex) {
			// Conexión rechazada, timeout de conexión o de lectura.
			throw new ServicioNoDisponibleException(SERVICIO, "Mercado Pago no está disponible", ex);
		}
	}

	/** 5xx o 429 (límite de pedidos): la doc recomienda reintentar más tarde. */
	private static boolean esTransitorio(HttpStatusCode status) {
		return status.is5xxServerError() || status.isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS);
	}

	private static ServicioNoDisponibleException transitorio(HttpStatusCode status) {
		return new ServicioNoDisponibleException(SERVICIO, "Mercado Pago respondió " + status.value(), null);
	}

	/**
	 * Body de {@code POST /v1/orders} con los nombres de la API. Sin {@code payer}: en prueba paga
	 * la cuenta compradora de prueba y no hace falta mandar datos del cliente. Sin back URLs
	 * todavía (no hay frontend). Un único ítem igual al total, que es lo que ve el comprador.
	 */
	record PedidoDeOrder(
			String type,
			@JsonProperty("processing_mode") String processingMode,
			@JsonProperty("total_amount") String totalAmount,
			@JsonProperty("external_reference") String externalReference,
			@JsonProperty("expiration_time") String expirationTime,
			String description,
			List<Item> items) {

		record Item(String title, @JsonProperty("unit_price") String unitPrice, int quantity) {
		}

		static PedidoDeOrder desde(NuevaOrder nueva) {
			// La API recibe los montos como string con dos decimales ("1000.00").
			String monto = nueva.monto().setScale(2, RoundingMode.UNNECESSARY).toPlainString();
			return new PedidoDeOrder("online", "manual", monto, nueva.externalReference(),
					// Duration.toString() ya es ISO 8601: PT9M, PT1H10M.
					nueva.vigencia().toString(), nueva.descripcion(),
					List.of(new Item(nueva.descripcion(), monto, 1)));
		}

	}

}
