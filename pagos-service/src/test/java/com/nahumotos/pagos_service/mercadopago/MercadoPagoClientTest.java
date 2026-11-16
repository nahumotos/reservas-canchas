package com.nahumotos.pagos_service.mercadopago;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.nahumotos.pagos_service.error.ServicioNoDisponibleException;

/**
 * Contrato HTTP con la API de Orders. Las respuestas son las de la documentación oficial de
 * Mercado Pago (create-order, get-order, refund-order), con nuestros valores.
 */
class MercadoPagoClientTest {

	private static final String BASE = "https://api.mercadopago.test";
	private static final String TOKEN = "TEST-1234567890-token-de-prueba";
	private static final String ORDER_ID = "ORD01JQ4S4KYYWFQFM2FF4XRTA1MX";
	private static final UUID CLAVE = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");

	private MockRestServiceServer servidor;
	private MercadoPagoClient cliente;

	@BeforeEach
	void armarCliente() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
		servidor = MockRestServiceServer.bindTo(builder).build();
		cliente = new MercadoPagoClient(builder.build(), TOKEN);
	}

	@Test
	void crearOrder_mandaLaOrderDeCheckoutProSinPayerNiBackUrls() {
		servidor.expect(requestTo(BASE + "/v1/orders"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", "Bearer " + TOKEN))
				.andExpect(header("X-Idempotency-Key", CLAVE.toString()))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				// STRICT: ningún campo de más (ni payer, ni config con back URLs).
				.andExpect(content().json("""
						{
						  "type": "online",
						  "processing_mode": "manual",
						  "total_amount": "25000.00",
						  "external_reference": "42",
						  "expiration_time": "PT9M",
						  "description": "Reserva de cancha #7",
						  "items": [
						    {"title": "Reserva de cancha #7", "unit_price": "25000.00", "quantity": 1}
						  ]
						}
						""", JsonCompareMode.STRICT))
				.andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
						.body(fixture("order-creada.json")));

		// Monto sin decimales: la API pide un string con dos.
		OrderMp order = cliente.crearOrder(
				new NuevaOrder("42", new BigDecimal("25000"), Duration.ofMinutes(9), "Reserva de cancha #7"), CLAVE);

		assertThat(order).isEqualTo(new OrderMp(ORDER_ID, "created", "created", "42", new BigDecimal("25000.00"),
				"https://www.mercadopago.com.ar/checkout/v1/redirect?order_id=" + ORDER_ID));
	}

	@Test
	void crearOrder_vigenciaDeMasDeUnaHora_enIso8601() {
		servidor.expect(requestTo(BASE + "/v1/orders"))
				.andExpect(content().json("{\"expiration_time\": \"PT1H10M\"}", JsonCompareMode.LENIENT))
				.andRespond(withSuccess(fixture("order-creada.json"), MediaType.APPLICATION_JSON));

		cliente.crearOrder(new NuevaOrder("42", new BigDecimal("25000.00"), Duration.ofMinutes(70), "Reserva"), CLAVE);

		servidor.verify();
	}

	@Test
	void crearOrder_sinConexion_lanzaServicioNoDisponible() {
		servidor.expect(requestTo(BASE + "/v1/orders")).andRespond(withException(new ConnectException()));

		assertThatThrownBy(() -> cliente.crearOrder(nuevaOrder(), CLAVE))
				.isInstanceOf(ServicioNoDisponibleException.class);
	}

	@Test
	void crearOrder_500_lanzaServicioNoDisponible() {
		servidor.expect(requestTo(BASE + "/v1/orders")).andRespond(withServerError());

		assertThatThrownBy(() -> cliente.crearOrder(nuevaOrder(), CLAVE))
				.isInstanceOf(ServicioNoDisponibleException.class);
	}

	@Test
	void crearOrder_429_lanzaServicioNoDisponible() {
		servidor.expect(requestTo(BASE + "/v1/orders")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

		assertThatThrownBy(() -> cliente.crearOrder(nuevaOrder(), CLAVE))
				.isInstanceOf(ServicioNoDisponibleException.class);
	}

	@Test
	void crearOrder_401_esUnErrorNuestroYSePropaga() {
		// Token inválido: reintentar no lo arregla, no es un 503.
		servidor.expect(requestTo(BASE + "/v1/orders")).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
				.contentType(MediaType.APPLICATION_JSON)
				.body("{\"errors\":[{\"code\":\"invalid_credentials\",\"message\":\"invalid access token\"}]}"));

		assertThatThrownBy(() -> cliente.crearOrder(nuevaOrder(), CLAVE))
				.isInstanceOf(RestClientResponseException.class)
				.isNotInstanceOf(ServicioNoDisponibleException.class);
	}

	@Test
	void obtenerOrder_200_devuelveElEstado() {
		servidor.expect(requestTo(BASE + "/v1/orders/" + ORDER_ID))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header("Authorization", "Bearer " + TOKEN))
				.andRespond(withSuccess(fixture("order-procesada.json"), MediaType.APPLICATION_JSON));

		assertThat(cliente.obtenerOrder(ORDER_ID)).contains(new OrderMp(ORDER_ID, "processed", "accredited", "42",
				new BigDecimal("25000.00"), null));
	}

	@Test
	void obtenerOrder_404_vacio() {
		servidor.expect(requestTo(BASE + "/v1/orders/ORDNOEXISTE")).andRespond(withStatus(HttpStatus.NOT_FOUND)
				.contentType(MediaType.APPLICATION_JSON)
				.body("{\"errors\":[{\"code\":\"order_not_found\",\"message\":\"order not found\"}]}"));

		assertThat(cliente.obtenerOrder("ORDNOEXISTE")).isEqualTo(Optional.empty());
	}

	@Test
	void obtenerOrder_500_lanzaServicioNoDisponible() {
		servidor.expect(requestTo(BASE + "/v1/orders/" + ORDER_ID)).andRespond(withServerError());

		assertThatThrownBy(() -> cliente.obtenerOrder(ORDER_ID)).isInstanceOf(ServicioNoDisponibleException.class);
	}

	@Test
	void reembolsar_201_reembolsoTotalConBodyVacio() {
		servidor.expect(requestTo(BASE + "/v1/orders/" + ORDER_ID + "/refund"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Authorization", "Bearer " + TOKEN))
				.andExpect(header("X-Idempotency-Key", CLAVE.toString()))
				// Sin body = reembolso total, según la doc.
				.andExpect(content().string(""))
				.andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
						.body(fixture("order-reembolsada.json")));

		assertThat(cliente.reembolsar(ORDER_ID, CLAVE)).isTrue();
	}

	@Test
	void reembolsar_409_false() {
		servidor.expect(requestTo(BASE + "/v1/orders/" + ORDER_ID + "/refund")).andRespond(
				withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
						.body("{\"errors\":[{\"code\":\"order_already_refunded\",\"message\":\"order already refunded\"}]}"));

		assertThat(cliente.reembolsar(ORDER_ID, CLAVE)).isFalse();
	}

	@Test
	void reembolsar_timeout_lanzaServicioNoDisponible() {
		servidor.expect(requestTo(BASE + "/v1/orders/" + ORDER_ID + "/refund"))
				.andRespond(withException(new HttpTimeoutException("timeout")));

		assertThatThrownBy(() -> cliente.reembolsar(ORDER_ID, CLAVE)).isInstanceOf(ServicioNoDisponibleException.class);
	}

	private static NuevaOrder nuevaOrder() {
		return new NuevaOrder("42", new BigDecimal("25000.00"), Duration.ofMinutes(9), "Reserva de cancha #7");
	}

	private static String fixture(String nombre) {
		try {
			return new ClassPathResource("mercadopago/" + nombre).getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
