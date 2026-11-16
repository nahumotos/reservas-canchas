package com.nahumotos.pagos_service;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Firma notificaciones como lo haría Mercado Pago, para los tests de integración del webhook.
 * El algoritmo está fijado con vectores calculados por fuera en FirmaDeMercadoPagoTest; acá
 * sólo hace falta poder firmar ids arbitrarios.
 */
public final class FirmasDePrueba {

	/** El mismo que PruebaDeIntegracion pone en mercadopago.webhook-secret. */
	public static final String SECRETO = "secreto-de-prueba";

	private FirmasDePrueba() {
	}

	public static String xSignature(String ts, String requestId, String dataId) {
		String manifest = "id:" + dataId.toLowerCase(Locale.ROOT) + ";request-id:" + requestId + ";ts:" + ts + ";";
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(SECRETO.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return "ts=" + ts + ",v1=" + HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

}
