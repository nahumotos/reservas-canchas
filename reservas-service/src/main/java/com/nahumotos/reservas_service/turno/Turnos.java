package com.nahumotos.reservas_service.turno;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Reglas de los turnos: duran una hora, empiezan en punto y tienen que caber enteros
 * dentro del horario del club. Todas las horas son hora local del club.
 */
public final class Turnos {

	public static final Duration DURACION = Duration.ofHours(1);

	private static final int MINUTOS_POR_DIA = 24 * 60;
	private static final int MINUTOS_POR_TURNO = (int) DURACION.toMinutes();

	private Turnos() {
	}

	/**
	 * Horas de inicio de los turnos de una fecha, en orden.
	 * <p>
	 * Si el club cierra a la medianoche o después (cierre &lt;= apertura, p. ej. 20:00 a 02:00),
	 * la fecha tiene dos tramos abiertos: la madrugada hasta el cierre y la noche desde la
	 * apertura hasta las 24:00. Por eso se trabaja en minutos del día y no con LocalTime,
	 * que da la vuelta a las 00:00. Con apertura igual a cierre, el club abre las 24 horas.
	 */
	public static List<LocalTime> delDia(LocalTime apertura, LocalTime cierre) {
		int abre = apertura.toSecondOfDay() / 60;
		int cierra = cierre.toSecondOfDay() / 60;
		List<int[]> tramosAbiertos = cierra > abre
				? List.of(new int[] { abre, cierra })
				: List.of(new int[] { 0, cierra }, new int[] { abre, MINUTOS_POR_DIA });

		List<LocalTime> turnos = new ArrayList<>();
		for (int hora = 0; hora < 24; hora++) {
			int inicio = hora * 60;
			int fin = inicio + MINUTOS_POR_TURNO;
			boolean cabe = tramosAbiertos.stream().anyMatch(tramo -> inicio >= tramo[0] && fin <= tramo[1]);
			if (cabe) {
				turnos.add(LocalTime.of(hora, 0));
			}
		}
		return turnos;
	}

	public static boolean empiezaEnPunto(LocalTime hora) {
		return hora.getMinute() == 0 && hora.getSecond() == 0 && hora.getNano() == 0;
	}

	/**
	 * Si el turno ya empezó (o es pasado) según la hora local del reloj. Un turno en curso
	 * cuenta como empezado: no se puede reservar las 12:00 a las 12:10.
	 */
	public static boolean empezo(LocalDate fecha, LocalTime horaInicio, Clock reloj) {
		return !LocalDateTime.of(fecha, horaInicio).isAfter(LocalDateTime.now(reloj));
	}

	/** Último día que se puede reservar: hoy (en la zona del reloj) más {@code diasMaximos}. */
	public static LocalDate ultimoDiaReservable(int diasMaximos, Clock reloj) {
		return LocalDate.now(reloj).plusDays(diasMaximos);
	}

	public static boolean fueraDeAnticipacion(LocalDate fecha, int diasMaximos, Clock reloj) {
		return fecha.isAfter(ultimoDiaReservable(diasMaximos, reloj));
	}

	/** Momento en que empieza el turno: la hora local del club pasada a instante. */
	public static Instant inicio(LocalDate fecha, LocalTime horaInicio, ZoneId zonaDelClub) {
		return LocalDateTime.of(fecha, horaInicio).atZone(zonaDelClub).toInstant();
	}

}
