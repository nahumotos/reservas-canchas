package com.nahumotos.pagos_service.pago;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Estados de order de Checkout Pro (doc "Estados de la order") a estados de pago. */
class EstadosDeOrderTest {

	@ParameterizedTest
	@CsvSource({
			"processed, APROBADO",
			"failed,    RECHAZADO",
			"expired,   EXPIRADO",
			"canceled,  EXPIRADO",
			"refunded,  REEMBOLSADO" })
	void estadosQueCambianElPago(String status, EstadoPago esperado) {
		assertThat(EstadosDeOrder.estadoDePago(status)).contains(esperado);
	}

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = { "created", "processing", "action_required", "algo_nuevo" })
	void otrosEstados_noCambianElPago(String status) {
		assertThat(EstadosDeOrder.estadoDePago(status)).isEqualTo(Optional.empty());
	}

}
