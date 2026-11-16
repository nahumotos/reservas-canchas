package com.nahumotos.reservas_service.turno;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class TurnosTest {

	private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

	@Test
	void delDia_horarioNormal_turnosEnPuntoQueTerminanAntesDelCierre() {
		List<LocalTime> turnos = Turnos.delDia(LocalTime.of(8, 0), LocalTime.of(23, 0));

		assertThat(turnos).isEqualTo(horas(8, 22));
		assertThat(turnos).hasSize(15);
	}

	@Test
	void delDia_aperturaNoEnPunto_empiezaEnLaHoraSiguiente() {
		assertThat(Turnos.delDia(LocalTime.of(8, 30), LocalTime.of(12, 0)))
				.containsExactly(LocalTime.of(9, 0), LocalTime.of(10, 0), LocalTime.of(11, 0));
	}

	@Test
	void delDia_cierreDespuesDeMedianoche_incluyeMadrugadaYNoche() {
		assertThat(Turnos.delDia(LocalTime.of(20, 0), LocalTime.of(2, 0))).containsExactly(
				LocalTime.of(0, 0), LocalTime.of(1, 0),
				LocalTime.of(20, 0), LocalTime.of(21, 0), LocalTime.of(22, 0), LocalTime.of(23, 0));
	}

	@Test
	void delDia_cierreALaMedianoche_incluyeElTurnoDe23() {
		List<LocalTime> turnos = Turnos.delDia(LocalTime.of(8, 0), LocalTime.MIDNIGHT);

		assertThat(turnos).isEqualTo(horas(8, 23));
		assertThat(turnos).hasSize(16);
	}

	@Test
	void empiezaEnPunto_soloMinutosSegundosYNanosEnCero() {
		assertThat(Turnos.empiezaEnPunto(LocalTime.of(18, 0))).isTrue();
		assertThat(Turnos.empiezaEnPunto(LocalTime.of(18, 30))).isFalse();
		assertThat(Turnos.empiezaEnPunto(LocalTime.of(18, 0, 1))).isFalse();
	}

	@Test
	void empezo_usaLaZonaDelRelojNoUtc() {
		// 02:00 UTC del 10/06 son las 23:00 del 09/06 en Argentina.
		Clock reloj = Clock.fixed(Instant.parse("2030-06-10T02:00:00Z"), ARGENTINA);

		assertThat(Turnos.empezo(LocalDate.of(2030, 6, 10), LocalTime.MIDNIGHT, reloj)).isFalse();
		assertThat(Turnos.empezo(LocalDate.of(2030, 6, 9), LocalTime.of(23, 0), reloj)).isTrue();
	}

	@Test
	void empezo_turnoEnCursoCuentaComoEmpezado() {
		LocalDate hoy = LocalDate.of(2030, 6, 10);
		Clock reloj = Clock.fixed(LocalDateTime.of(hoy, LocalTime.of(12, 10)).atZone(ARGENTINA).toInstant(), ARGENTINA);

		assertThat(Turnos.empezo(hoy, LocalTime.of(12, 0), reloj)).isTrue();
		assertThat(Turnos.empezo(hoy, LocalTime.of(13, 0), reloj)).isFalse();
	}

	@Test
	void fueraDeAnticipacion_cuentaDesdeElDiaLocalDelReloj() {
		// 15:00 UTC del 10/06 son las 12:00 del 10/06 en Argentina: el último día con 60 días es el 09/08.
		Clock mediodia = Clock.fixed(Instant.parse("2030-06-10T15:00:00Z"), ARGENTINA);
		assertThat(Turnos.fueraDeAnticipacion(LocalDate.of(2030, 8, 9), 60, mediodia)).isFalse();
		assertThat(Turnos.fueraDeAnticipacion(LocalDate.of(2030, 8, 10), 60, mediodia)).isTrue();

		// 02:00 UTC del 10/06 todavía es el 09/06 en Argentina: el límite pasa a ser el 08/08.
		Clock noche = Clock.fixed(Instant.parse("2030-06-10T02:00:00Z"), ARGENTINA);
		assertThat(Turnos.fueraDeAnticipacion(LocalDate.of(2030, 8, 9), 60, noche)).isTrue();
	}

	@Test
	void inicio_convierteLaHoraLocalDelClubAInstante() {
		assertThat(Turnos.inicio(LocalDate.of(2030, 6, 10), LocalTime.NOON, ARGENTINA))
				.isEqualTo(Instant.parse("2030-06-10T15:00:00Z"));
	}

	private static List<LocalTime> horas(int desde, int hasta) {
		return IntStream.rangeClosed(desde, hasta).mapToObj(h -> LocalTime.of(h, 0)).toList();
	}

}
