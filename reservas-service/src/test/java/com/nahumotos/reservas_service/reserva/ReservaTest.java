package com.nahumotos.reservas_service.reserva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import com.nahumotos.reservas_service.error.ConflictoException;

/** Máquina de estados de la reserva (sin base ni Spring). */
class ReservaTest {

	private static final Instant CREADA = Instant.parse("2030-06-10T15:00:00Z");
	private static final Instant VENCE = CREADA.plus(Duration.ofMinutes(10));
	private static final Instant ANTES_DE_VENCER = VENCE.minusSeconds(1);

	@Test
	void confirmar_pendienteNoVencida_quedaConfirmada() {
		Reserva reserva = pendiente();

		reserva.confirmar(ANTES_DE_VENCER);

		assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
	}

	@Test
	void cancelar_pendienteNoVencida_quedaCancelada() {
		Reserva reserva = pendiente();

		reserva.cancelar();

		assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
	}

	@Test
	void confirmar_pendienteVencida_lanzaConflicto() {
		Reserva reserva = pendiente();

		// Justo en venceEn ya está vencida, igual que el "<=" de la tarea de expiración.
		assertThatThrownBy(() -> reserva.confirmar(VENCE))
				.isInstanceOf(ConflictoException.class)
				.hasMessageContaining("venció");
		assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PENDIENTE_PAGO);
	}

	@Test
	void cancelar_pendienteVencida_quedaCancelada() {
		Reserva reserva = pendiente();

		reserva.cancelar();

		assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
	}

	@Test
	void confirmar_confirmada_esIdempotente() {
		Reserva reserva = pendiente();
		reserva.confirmar(ANTES_DE_VENCER);

		// Un reintento del pago, aunque llegue después de venceEn, no es un error.
		reserva.confirmar(VENCE.plus(Duration.ofHours(1)));

		assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
	}

	@Test
	void cancelar_confirmada_quedaCancelada() {
		Reserva reserva = pendiente();
		reserva.confirmar(ANTES_DE_VENCER);

		reserva.cancelar();

		assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
	}

	@Test
	void confirmar_cancelada_lanzaConflicto() {
		Reserva reserva = pendiente();
		reserva.cancelar();

		assertThatThrownBy(() -> reserva.confirmar(ANTES_DE_VENCER))
				.isInstanceOf(ConflictoException.class)
				.hasMessageContaining("cancelada");
	}

	@Test
	void cancelar_cancelada_esIdempotente() {
		Reserva reserva = pendiente();
		reserva.cancelar();

		reserva.cancelar();

		assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
	}

	private static Reserva pendiente() {
		return new Reserva(10L, 1L, LocalDate.of(2030, 6, 11), LocalTime.of(18, 0), "Ana", "ana@mail.com",
				new BigDecimal("25000.00"), CREADA, VENCE);
	}

}
