package com.nahumotos.reservas_service.reserva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import com.nahumotos.reservas_service.PruebaDeIntegracion;

class ReservaRepositoryIntegrationTest extends PruebaDeIntegracion {

	@Test
	void indiceParcial_rechazaSegundaReservaActivaDelMismoTurno() {
		reservaRepository.saveAndFlush(nuevaReserva(LocalTime.of(18, 0)));

		assertThatThrownBy(() -> reservaRepository.saveAndFlush(nuevaReserva(LocalTime.of(18, 0))))
				.isInstanceOf(DataIntegrityViolationException.class)
				.satisfies(ex -> assertThat(((DataIntegrityViolationException) ex).getMostSpecificCause().getMessage())
						.contains("ux_reserva_turno_activo"));
	}

	@Test
	void indiceParcial_permiteReservarDeNuevoUnTurnoCancelado() {
		reservaRepository.saveAndFlush(nuevaReserva(LocalTime.of(18, 0)));
		jdbc.update("update reserva set estado = 'CANCELADA'");

		reservaRepository.saveAndFlush(nuevaReserva(LocalTime.of(18, 0)));

		assertThat(reservaRepository.count()).isEqualTo(2);
	}

	@Test
	void constructor_calculaHoraFinYArrancaPendientePago() {
		Reserva guardada = reservaRepository.saveAndFlush(nuevaReserva(LocalTime.of(18, 0)));

		assertThat(guardada.getHoraFin()).isEqualTo(LocalTime.of(19, 0));
		assertThat(guardada.getEstado()).isEqualTo(EstadoReserva.PENDIENTE_PAGO);
		assertThat(guardada.getVersion()).isZero();
	}

	private Reserva nuevaReserva(LocalTime horaInicio) {
		Instant ahora = reloj.instant();
		return new Reserva(CANCHA_ID, CLUB_ID, MANIANA, horaInicio, "Ana", "ana@mail.com",
				new BigDecimal("25000.00"), ahora, ahora.plus(Duration.ofMinutes(10)));
	}

}
