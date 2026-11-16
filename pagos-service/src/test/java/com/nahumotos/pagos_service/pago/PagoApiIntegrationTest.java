package com.nahumotos.pagos_service.pago;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.nahumotos.pagos_service.PruebaDeIntegracion;
import com.nahumotos.pagos_service.error.RecursoNoEncontradoException;
import com.nahumotos.pagos_service.error.ServicioNoDisponibleException;
import com.nahumotos.pagos_service.mercadopago.NuevaOrder;
import com.nahumotos.pagos_service.mercadopago.OrderMp;
import com.nahumotos.pagos_service.reservas.ReservaRemota;

/**
 * POST /api/pagos y GET /api/pagos/{id}. Escenario: la reserva 7 está pendiente de pago, cuesta
 * 25000.00 y vence 9 minutos y 30 segundos después de AHORA.
 */
class PagoApiIntegrationTest extends PruebaDeIntegracion {

	private static final Long RESERVA_ID = 7L;
	private static final String CHECKOUT_URL = "https://www.mercadopago.com.ar/checkout/v1/redirect?order_id=ORD1";

	@BeforeEach
	void reservaPendienteYMercadoPagoRespondiendo() {
		reservaQueVenceEn(Duration.ofSeconds(570));
		when(mercadoPago.crearOrder(any(), any()))
				.thenReturn(new OrderMp("ORD1", "created", "created", null, new BigDecimal("25000.00"), CHECKOUT_URL));
	}

	@Test
	void crear_201ConCheckoutUrl() throws Exception {
		MvcResult resultado = crear(RESERVA_ID)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.reservaId").value(7))
				.andExpect(jsonPath("$.monto").value(25000.0))
				.andExpect(jsonPath("$.estado").value("PENDIENTE"))
				.andExpect(jsonPath("$.checkoutUrl").value(CHECKOUT_URL))
				.andReturn();

		Integer pagoId = JsonPath.read(resultado.getResponse().getContentAsString(), "$.pagoId");
		assertThat(resultado.getResponse().getHeader("Location")).endsWith("/api/pagos/" + pagoId);
		assertThat(jdbc.queryForObject("select mp_order_id from pago where id = ?", String.class, pagoId))
				.isEqualTo("ORD1");
	}

	@Test
	void crear_armaLaOrderConElPagoComoReferenciaYLaVigenciaHastaElVencimiento() throws Exception {
		Integer pagoId = JsonPath.read(crear(RESERVA_ID).andReturn().getResponse().getContentAsString(), "$.pagoId");

		ArgumentCaptor<NuevaOrder> order = ArgumentCaptor.forClass(NuevaOrder.class);
		ArgumentCaptor<UUID> clave = ArgumentCaptor.forClass(UUID.class);
		verify(mercadoPago).crearOrder(order.capture(), clave.capture());
		assertThat(order.getValue().externalReference()).isEqualTo(pagoId.toString());
		assertThat(order.getValue().monto()).isEqualByComparingTo("25000.00");
		// Faltan 9 min 30 s para que venza la reserva: la order dura 9 min (redondeo hacia abajo).
		assertThat(order.getValue().vigencia()).isEqualTo(Duration.ofMinutes(9));
		assertThat(clave.getValue()).isEqualTo(
				jdbc.queryForObject("select clave_idempotencia from pago where id = ?", UUID.class, pagoId));
	}

	@Test
	void crear_conUnPagoPendiente_devuelveEseMismoCon200() throws Exception {
		Integer primero = JsonPath.read(crear(RESERVA_ID).andExpect(status().isCreated()).andReturn()
				.getResponse().getContentAsString(), "$.pagoId");

		crear(RESERVA_ID)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pagoId").value(primero))
				.andExpect(jsonPath("$.checkoutUrl").value(CHECKOUT_URL));

		verify(mercadoPago, times(1)).crearOrder(any(), any());
		assertThat(jdbc.queryForObject("select count(*) from pago", Integer.class)).isEqualTo(1);
	}

	@Test
	void crear_reservaInexistente_404() throws Exception {
		when(reservasClient.obtenerReserva(99L)).thenThrow(new RecursoNoEncontradoException("No existe la reserva con id 99"));

		crear(99L)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("No existe la reserva con id 99"));
	}

	@Test
	void crear_reservasServiceCaido_503() throws Exception {
		when(reservasClient.obtenerReserva(RESERVA_ID))
				.thenThrow(new ServicioNoDisponibleException("reservas-service", "reservas-service no está disponible", null));

		crear(RESERVA_ID)
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.detail").value(
						"No pudimos comunicarnos con reservas-service. Probá de nuevo en unos segundos."));
		verify(mercadoPago, never()).crearOrder(any(), any());
	}

	@Test
	void crear_reservaQueNoEstaPendienteDePago_409() throws Exception {
		when(reservasClient.obtenerReserva(RESERVA_ID)).thenReturn(
				new ReservaRemota(RESERVA_ID, new BigDecimal("25000.00"), "CONFIRMADA", AHORA.plusSeconds(570)));

		crear(RESERVA_ID).andExpect(status().isConflict());
		verify(mercadoPago, never()).crearOrder(any(), any());
	}

	@Test
	void crear_reservaQueVenceEnMenosDeUnMinuto_409() throws Exception {
		reservaQueVenceEn(Duration.ofSeconds(59));

		crear(RESERVA_ID).andExpect(status().isConflict());
		verify(mercadoPago, never()).crearOrder(any(), any());
	}

	@Test
	void crear_reservaQueVenceEnJustoUnMinuto_201ConOrderDeUnMinuto() throws Exception {
		reservaQueVenceEn(Duration.ofMinutes(1));

		crear(RESERVA_ID).andExpect(status().isCreated());

		ArgumentCaptor<NuevaOrder> order = ArgumentCaptor.forClass(NuevaOrder.class);
		verify(mercadoPago).crearOrder(order.capture(), any());
		assertThat(order.getValue().vigencia()).isEqualTo(Duration.ofMinutes(1));
	}

	@Test
	void crear_reservaConUnPagoYaAprobado_409() throws Exception {
		// Mercado Pago ya aprobó un pago, pero reservas-service todavía no procesó el evento.
		jdbc.update("""
				insert into pago (reserva_id, monto, estado, mp_order_id, clave_idempotencia, creado_en, actualizado_en)
				values (7, 25000.00, 'APROBADO', 'ORD0', ?, now(), now())
				""", UUID.randomUUID());

		crear(RESERVA_ID)
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.detail").value("La reserva 7 ya tiene un pago aprobado"));
		verify(mercadoPago, never()).crearOrder(any(), any());
	}

	@Test
	void crear_sinReservaId_400() throws Exception {
		mockMvc.perform(post("/api/pagos").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errores[0].campo").value("reservaId"));
	}

	@Test
	void crear_mercadoPagoCaido_503_yElReintentoUsaElMismoPagoYLaMismaClave() throws Exception {
		when(mercadoPago.crearOrder(any(), any()))
				.thenThrow(new ServicioNoDisponibleException("Mercado Pago", "Mercado Pago respondió 500", null))
				.thenReturn(new OrderMp("ORD1", "created", "created", null, new BigDecimal("25000.00"), CHECKOUT_URL));

		crear(RESERVA_ID)
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.detail").value(
						"No pudimos comunicarnos con Mercado Pago. Probá de nuevo en unos segundos."));
		crear(RESERVA_ID)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.checkoutUrl").value(CHECKOUT_URL));

		ArgumentCaptor<UUID> claves = ArgumentCaptor.forClass(UUID.class);
		verify(mercadoPago, times(2)).crearOrder(any(), claves.capture());
		List<UUID> usadas = claves.getAllValues();
		// Misma X-Idempotency-Key: si el primer intento llegó a crear la order, MP devuelve esa.
		assertThat(usadas.get(1)).isEqualTo(usadas.get(0));
		assertThat(jdbc.queryForObject("select count(*) from pago", Integer.class)).isEqualTo(1);
	}

	@Test
	void obtener_200() throws Exception {
		Integer pagoId = JsonPath.read(crear(RESERVA_ID).andReturn().getResponse().getContentAsString(), "$.pagoId");

		mockMvc.perform(get("/api/pagos/{id}", pagoId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.pagoId").value(pagoId))
				.andExpect(jsonPath("$.estado").value("PENDIENTE"));
	}

	@Test
	void obtener_inexistente_404() throws Exception {
		mockMvc.perform(get("/api/pagos/{id}", 999_999))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("No existe el pago con id 999999"));
	}

	private void reservaQueVenceEn(Duration falta) {
		Instant venceEn = AHORA.plus(falta);
		when(reservasClient.obtenerReserva(RESERVA_ID))
				.thenReturn(new ReservaRemota(RESERVA_ID, new BigDecimal("25000.00"), "PENDIENTE_PAGO", venceEn));
	}

	private ResultActions crear(Long reservaId) throws Exception {
		return mockMvc.perform(post("/api/pagos").contentType(MediaType.APPLICATION_JSON)
				.content("{\"reservaId\": " + reservaId + "}"));
	}

}
