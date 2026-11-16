package com.nahumotos.pagos_service.mercadopago;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Validación de x-signature. Los hashes esperados se calcularon por fuera del código (con
 * openssl), sobre el manifest que describe la documentación de Mercado Pago:
 * <pre>
 * printf '%s' 'id:ord01jq4s4kyywfqfm2ff4xrta1mx;request-id:bb56a2f1-6aae-46ac-982e-9dcd3581d08e;ts:1742505638683;' \
 *   | openssl dgst -sha256 -hmac 'secreto-de-prueba'
 * </pre>
 */
class FirmaDeMercadoPagoTest {

	private static final String SECRETO = "secreto-de-prueba";
	/** Así llega en el query param data.id: en mayúsculas. */
	private static final String DATA_ID = "ORD01JQ4S4KYYWFQFM2FF4XRTA1MX";
	private static final String REQUEST_ID = "bb56a2f1-6aae-46ac-982e-9dcd3581d08e";
	private static final String TS = "1742505638683";

	/** HMAC de "id:ord01jq4s4kyywfqfm2ff4xrta1mx;request-id:bb56a2f1-...;ts:1742505638683;". */
	private static final String V1 = "3674510c7435250ab0c97304c20a13e7d4c50c382662d5cb7daff84af5ea3039";
	/** HMAC de "id:ord01jq4s4kyywfqfm2ff4xrta1mx;ts:1742505638683;" (sin request-id). */
	private static final String V1_SIN_REQUEST_ID = "2cc45910999067711555ee1b88b4f6fbb51013daa8ba1fec10e15599744a77dd";
	/** HMAC del manifest con el id en mayúsculas: lo que daría no pasarlo a minúsculas. */
	private static final String V1_ID_EN_MAYUSCULAS = "f0cda6ee161283b10a0a1847321e5c73f84adeebcbd16447841988686c89a90c";

	private final FirmaDeMercadoPago firma = new FirmaDeMercadoPago(SECRETO);

	@Test
	void firmaValida_conDataIdEnMayusculas_esValida() {
		assertThat(firma.esValida("ts=" + TS + ",v1=" + V1, REQUEST_ID, DATA_ID)).isTrue();
	}

	@Test
	void firmaCalculadaSinPasarElIdAMinusculas_noEsValida() {
		assertThat(firma.esValida("ts=" + TS + ",v1=" + V1_ID_EN_MAYUSCULAS, REQUEST_ID, DATA_ID)).isFalse();
	}

	@Test
	void hashAlterado_noEsValida() {
		String alterado = V1.substring(0, V1.length() - 1) + "0";

		assertThat(firma.esValida("ts=" + TS + ",v1=" + alterado, REQUEST_ID, DATA_ID)).isFalse();
	}

	@Test
	void otroTimestamp_noEsValida() {
		assertThat(firma.esValida("ts=1742505638684,v1=" + V1, REQUEST_ID, DATA_ID)).isFalse();
	}

	@Test
	void otroSecreto_noEsValida() {
		assertThat(new FirmaDeMercadoPago("otro-secreto").esValida("ts=" + TS + ",v1=" + V1, REQUEST_ID, DATA_ID))
				.isFalse();
	}

	@Test
	void headerFaltanteOVacio_noEsValida() {
		assertThat(firma.esValida(null, REQUEST_ID, DATA_ID)).isFalse();
		assertThat(firma.esValida("  ", REQUEST_ID, DATA_ID)).isFalse();
	}

	@Test
	void headerSinTsOSinV1_noEsValida() {
		assertThat(firma.esValida("v1=" + V1, REQUEST_ID, DATA_ID)).isFalse();
		assertThat(firma.esValida("ts=" + TS, REQUEST_ID, DATA_ID)).isFalse();
		assertThat(firma.esValida("cualquier cosa", REQUEST_ID, DATA_ID)).isFalse();
	}

	@Test
	void sinRequestId_seOmiteEsePar() {
		assertThat(firma.esValida("ts=" + TS + ",v1=" + V1_SIN_REQUEST_ID, null, DATA_ID)).isTrue();
		// Con el par omitido, la firma que sí incluye request-id ya no coincide.
		assertThat(firma.esValida("ts=" + TS + ",v1=" + V1, null, DATA_ID)).isFalse();
	}

	@Test
	void espaciosAlrededorDeLasPartes_seIgnoran() {
		assertThat(firma.esValida(" ts = " + TS + " , v1 = " + V1 + " ", REQUEST_ID, DATA_ID)).isTrue();
	}

}
