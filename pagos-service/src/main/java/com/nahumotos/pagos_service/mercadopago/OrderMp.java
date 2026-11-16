package com.nahumotos.pagos_service.mercadopago;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Los campos de una order de Mercado Pago que usamos. La respuesta real trae muchos más
 * (transactions, items, config…): se ignoran para no rompernos cuando agreguen campos.
 *
 * @param status      created, processing, action_required, processed, failed, refunded, canceled…
 * @param checkoutUrl adonde mandar al comprador; sólo viene al crear la order
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderMp(
		String id,
		String status,
		@JsonProperty("status_detail") String statusDetail,
		@JsonProperty("external_reference") String externalReference,
		@JsonProperty("total_amount") BigDecimal totalAmount,
		@JsonProperty("checkout_url") String checkoutUrl) {
}
