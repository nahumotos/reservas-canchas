package com.nahumotos.pagos_service.mercadopago;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Lo que necesitamos para crear una order de Checkout Pro.
 *
 * @param externalReference id de nuestro pago: con él reconocemos la order en el webhook
 * @param monto             total a cobrar
 * @param vigencia          tiempo que la order acepta pagos (expiration_time)
 * @param descripcion       lo que ve el comprador en el checkout
 */
public record NuevaOrder(String externalReference, BigDecimal monto, Duration vigencia, String descripcion) {
}
