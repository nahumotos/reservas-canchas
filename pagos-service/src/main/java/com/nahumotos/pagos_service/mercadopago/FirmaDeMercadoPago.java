package com.nahumotos.pagos_service.mercadopago;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Valida el header {@code x-signature} de los webhooks, como lo describe la documentación de
 * Mercado Pago: del header ({@code ts=...,v1=...}) se toman {@code ts} y {@code v1}; se arma el
 * manifest {@code id:<data.id>;request-id:<x-request-id>;ts:<ts>;} con {@code data.id} en
 * minúsculas y omitiendo los pares que no vinieron; se calcula HMAC-SHA256 en hexadecimal con la
 * firma secreta y se compara con {@code v1} en tiempo constante.
 */
public class FirmaDeMercadoPago {

	private static final String ALGORITMO = "HmacSHA256";

	private final SecretKeySpec clave;

	public FirmaDeMercadoPago(String secreto) {
		this.clave = new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), ALGORITMO);
	}

	public boolean esValida(String xSignature, String xRequestId, String dataId) {
		if (estaVacio(xSignature)) {
			return false;
		}
		String ts = null;
		String v1 = null;
		for (String parte : xSignature.split(",")) {
			int igual = parte.indexOf('=');
			if (igual < 0) {
				continue;
			}
			String clave = parte.substring(0, igual).trim();
			String valor = parte.substring(igual + 1).trim();
			if (clave.equals("ts")) {
				ts = valor;
			}
			else if (clave.equals("v1")) {
				v1 = valor;
			}
		}
		if (estaVacio(ts) || estaVacio(v1)) {
			return false;
		}

		StringBuilder manifest = new StringBuilder();
		if (!estaVacio(dataId)) {
			// Los ids alfanuméricos (las orders empiezan con "ORD") se firman en minúsculas.
			manifest.append("id:").append(dataId.toLowerCase(Locale.ROOT)).append(';');
		}
		if (!estaVacio(xRequestId)) {
			manifest.append("request-id:").append(xRequestId).append(';');
		}
		manifest.append("ts:").append(ts).append(';');

		byte[] esperado = hmac(manifest.toString()).getBytes(StandardCharsets.US_ASCII);
		byte[] recibido = v1.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
		// Comparación en tiempo constante: no revela cuántos caracteres del hash coinciden.
		return MessageDigest.isEqual(esperado, recibido);
	}

	private String hmac(String mensaje) {
		try {
			Mac mac = Mac.getInstance(ALGORITMO);
			mac.init(clave);
			return HexFormat.of().formatHex(mac.doFinal(mensaje.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException | InvalidKeyException ex) {
			// HmacSHA256 viene en todas las JVM: si falta, es un problema de la instalación.
			throw new IllegalStateException("No se pudo calcular HMAC-SHA256", ex);
		}
	}

	private static boolean estaVacio(String valor) {
		return valor == null || valor.isBlank();
	}

}
