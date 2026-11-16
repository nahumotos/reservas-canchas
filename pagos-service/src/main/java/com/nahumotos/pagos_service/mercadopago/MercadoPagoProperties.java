package com.nahumotos.pagos_service.mercadopago;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Propiedades {@code mercadopago.*}. Las dos credenciales no tienen valor por defecto: si
 * faltan (o están vacías, como en .env.example) el servicio no arranca y el mensaje dice qué
 * variable de entorno completar. Los records no tienen toString propio con los secretos
 * enmascarados, así que nunca hay que logear este objeto.
 *
 * @param urlBase         API de Mercado Pago (la misma para prueba y producción)
 * @param accessToken     Access Token de la aplicación; el de prueba hace que todo sea de prueba
 * @param webhookSecret   "firma secreta" de los webhooks, para validar x-signature
 * @param timeoutConexion cuánto esperar para conectar
 * @param timeoutLectura  cuánto esperar la respuesta
 */
@Validated
@ConfigurationProperties("mercadopago")
public record MercadoPagoProperties(
		@NotNull URI urlBase,
		@NotBlank(message = "falta MP_ACCESS_TOKEN (Access Token de prueba de Mercado Pago) en el .env de la raíz")
		String accessToken,
		@NotBlank(message = "falta MP_WEBHOOK_SECRET (firma secreta de los webhooks de Mercado Pago) en el .env de la raíz")
		String webhookSecret,
		@NotNull Duration timeoutConexion,
		@NotNull Duration timeoutLectura) {
}
