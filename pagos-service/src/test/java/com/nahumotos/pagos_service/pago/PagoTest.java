package com.nahumotos.pagos_service.pago;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Reglas del pago que no necesitan base ni Spring. */
class PagoTest {

	private static final Instant AHORA = Instant.parse("2030-06-10T15:00:00Z");
	private static final UUID CLAVE = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");

	@Test
	void nuevo_quedaPendienteYSinOrder() {
		Pago pago = pago(CLAVE);

		assertThat(pago.getEstado()).isEqualTo(EstadoPago.PENDIENTE);
		assertThat(pago.tieneOrder()).isFalse();
	}

	@Test
	void claveDeReembolso_esSiempreLaMismaParaElMismoPago() {
		// Un reintento del reembolso tiene que mandar la misma X-Idempotency-Key, aunque lo haga
		// otro proceso que leyó el pago de la base.
		assertThat(pago(CLAVE).claveDeReembolso()).isEqualTo(pago(CLAVE).claveDeReembolso());
	}

	@Test
	void claveDeReembolso_noEsLaDeCrearLaOrderNiLaDeOtroPago() {
		Pago pago = pago(CLAVE);

		assertThat(pago.claveDeReembolso()).isNotEqualTo(CLAVE);
		assertThat(pago.claveDeReembolso()).isNotEqualTo(pago(UUID.randomUUID()).claveDeReembolso());
	}

	@Test
	void pendienteQueSeAprueba_quedaAprobadoYAvisaQueEsLaPrimeraVez() {
		Pago pago = pago(CLAVE);

		assertThat(pago.registrarEstadoDeOrder(EstadoPago.APROBADO, AHORA)).isTrue();
		assertThat(pago.getEstado()).isEqualTo(EstadoPago.APROBADO);
	}

	@Test
	void aprobadoQueSeApruebaDeNuevo_noEsUnaNuevaAprobacion() {
		// Notificación repetida: no tiene que generar otro pago.aprobado.
		Pago pago = aprobado();

		assertThat(pago.registrarEstadoDeOrder(EstadoPago.APROBADO, AHORA)).isFalse();
		assertThat(pago.getEstado()).isEqualTo(EstadoPago.APROBADO);
	}

	@ParameterizedTest
	@EnumSource(value = EstadoPago.class, names = { "RECHAZADO", "EXPIRADO", "REEMBOLSADO" })
	void pendiente_tomaElEstadoFinalDeLaOrder(EstadoPago nuevo) {
		Pago pago = pago(CLAVE);

		assertThat(pago.registrarEstadoDeOrder(nuevo, AHORA)).isFalse();
		assertThat(pago.getEstado()).isEqualTo(nuevo);
	}

	@ParameterizedTest
	@EnumSource(value = EstadoPago.class, names = { "RECHAZADO", "EXPIRADO" })
	void aprobado_noVuelveAtras(EstadoPago nuevo) {
		// Una notificación vieja que llega tarde no deshace una aprobación.
		Pago pago = aprobado();

		pago.registrarEstadoDeOrder(nuevo, AHORA);

		assertThat(pago.getEstado()).isEqualTo(EstadoPago.APROBADO);
	}

	@ParameterizedTest
	@EnumSource(value = EstadoPago.class, names = { "RECHAZADO", "EXPIRADO" })
	void rechazadoOExpiradoQueDespuesSeProcesa_pasaAAprobado(EstadoPago anterior) {
		// La order terminó cobrada: nunca se ignora, reservas-service decide si confirma o rechaza.
		Pago pago = pago(CLAVE);
		pago.registrarEstadoDeOrder(anterior, AHORA);

		assertThat(pago.registrarEstadoDeOrder(EstadoPago.APROBADO, AHORA)).isTrue();
		assertThat(pago.getEstado()).isEqualTo(EstadoPago.APROBADO);
	}

	@ParameterizedTest
	@EnumSource(value = EstadoPago.class, names = { "RECHAZADO", "EXPIRADO" })
	void cobroQueNoPuedeAprobarse_quedaParaReembolsar(EstadoPago anterior) {
		// La reserva ya tiene otro pago activo: este cobro sobra y se devuelve.
		Pago pago = pago(CLAVE);
		pago.registrarEstadoDeOrder(anterior, AHORA);

		pago.registrarCobroSobrante(AHORA);

		assertThat(pago.getEstado()).isEqualTo(EstadoPago.REEMBOLSO_PENDIENTE);
	}

	@Test
	void reembolsoPendiente_noVuelveAAprobado() {
		Pago pago = aprobado();
		pago.solicitarReembolso(AHORA);

		assertThat(pago.registrarEstadoDeOrder(EstadoPago.APROBADO, AHORA)).isFalse();
		assertThat(pago.getEstado()).isEqualTo(EstadoPago.REEMBOLSO_PENDIENTE);
	}

	@Test
	void aprobadoReembolsadoDesdeMercadoPago_quedaReembolsado() {
		Pago pago = aprobado();

		pago.registrarEstadoDeOrder(EstadoPago.REEMBOLSADO, AHORA);

		assertThat(pago.getEstado()).isEqualTo(EstadoPago.REEMBOLSADO);
	}

	@Test
	void expirado_noPasaAReembolsado() {
		// Nunca se cobró: no hay nada que devolver.
		Pago pago = pago(CLAVE);
		pago.registrarEstadoDeOrder(EstadoPago.EXPIRADO, AHORA);

		pago.registrarEstadoDeOrder(EstadoPago.REEMBOLSADO, AHORA);

		assertThat(pago.getEstado()).isEqualTo(EstadoPago.EXPIRADO);
	}

	@Test
	void aprobado_alSolicitarReembolso_quedaReembolsoPendiente() {
		Pago pago = aprobado();

		assertThat(pago.solicitarReembolso(AHORA)).isTrue();
		assertThat(pago.getEstado()).isEqualTo(EstadoPago.REEMBOLSO_PENDIENTE);
	}

	@Test
	void reembolsoYaPendiente_sigueParaReembolsar() {
		Pago pago = aprobado();
		pago.solicitarReembolso(AHORA);

		assertThat(pago.solicitarReembolso(AHORA)).isTrue();
		assertThat(pago.getEstado()).isEqualTo(EstadoPago.REEMBOLSO_PENDIENTE);
	}

	@ParameterizedTest
	@EnumSource(value = EstadoPago.class, names = { "RECHAZADO", "EXPIRADO", "REEMBOLSADO" })
	void sinPlataCobrada_noHayNadaQueReembolsar(EstadoPago estado) {
		Pago pago = pago(CLAVE);
		pago.registrarEstadoDeOrder(estado, AHORA);

		assertThat(pago.solicitarReembolso(AHORA)).isFalse();
		assertThat(pago.getEstado()).isEqualTo(estado);
	}

	@Test
	void pendienteDePagar_noSeReembolsa() {
		Pago pago = pago(CLAVE);

		assertThat(pago.solicitarReembolso(AHORA)).isFalse();
		assertThat(pago.getEstado()).isEqualTo(EstadoPago.PENDIENTE);
	}

	@Test
	void reembolsoPendiente_alConfirmarse_quedaReembolsado() {
		Pago pago = aprobado();
		pago.solicitarReembolso(AHORA);

		pago.marcarReembolsado(AHORA);

		assertThat(pago.getEstado()).isEqualTo(EstadoPago.REEMBOLSADO);
	}

	@Test
	void reembolsoPendienteConErrorPermanente_quedaFallido() {
		Pago pago = aprobado();
		pago.solicitarReembolso(AHORA);

		pago.marcarReembolsoFallido(AHORA);

		assertThat(pago.getEstado()).isEqualTo(EstadoPago.REEMBOLSO_FALLIDO);
	}

	@Test
	void reembolsoFallido_noSeVuelveAPedirSolo() {
		Pago pago = aprobado();
		pago.solicitarReembolso(AHORA);
		pago.marcarReembolsoFallido(AHORA);

		assertThat(pago.solicitarReembolso(AHORA)).isFalse();
		assertThat(pago.getEstado()).isEqualTo(EstadoPago.REEMBOLSO_FALLIDO);
	}

	@Test
	void reembolsoFallidoResueltoAManoDesdeElPanel_quedaReembolsado() {
		Pago pago = aprobado();
		pago.solicitarReembolso(AHORA);
		pago.marcarReembolsoFallido(AHORA);

		pago.registrarEstadoDeOrder(EstadoPago.REEMBOLSADO, AHORA);

		assertThat(pago.getEstado()).isEqualTo(EstadoPago.REEMBOLSADO);
	}

	private static Pago aprobado() {
		Pago pago = pago(CLAVE);
		pago.registrarEstadoDeOrder(EstadoPago.APROBADO, AHORA);
		return pago;
	}

	private static Pago pago(UUID clave) {
		return new Pago(7L, new BigDecimal("25000.00"), clave, AHORA);
	}

}
