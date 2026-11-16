package com.nahumotos.reservas_service.disponibilidad;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.nahumotos.reservas_service.canchas.CanchaRemota;
import com.nahumotos.reservas_service.canchas.CanchasClient;
import com.nahumotos.reservas_service.canchas.ClubRemoto;
import com.nahumotos.reservas_service.config.ReservasProperties;
import com.nahumotos.reservas_service.disponibilidad.DisponibilidadResponse.TurnoLibre;
import com.nahumotos.reservas_service.reserva.ReservaRepository;
import com.nahumotos.reservas_service.turno.Turnos;

@Service
public class DisponibilidadService {

	private final CanchasClient canchasClient;
	private final ReservaRepository reservaRepository;
	private final Clock reloj;
	private final int anticipacionMaximaDias;

	public DisponibilidadService(CanchasClient canchasClient, ReservaRepository reservaRepository, Clock reloj,
			ReservasProperties propiedades) {
		this.canchasClient = canchasClient;
		this.reservaRepository = reservaRepository;
		this.reloj = reloj;
		this.anticipacionMaximaDias = propiedades.anticipacionMaximaDias();
	}

	/**
	 * Aplica las mismas reglas que el alta de reservas (horario del club, turnos que ya
	 * empezaron, límite de anticipación, turnos ocupados), así lo que se muestra libre es lo
	 * que se puede reservar. Una cancha inactiva o una fecha pasada o fuera del límite de
	 * anticipación devuelven una lista vacía, no un error.
	 * Sin @Transactional por el mismo motivo que ReservaService.crear: no retener la
	 * conexión a la base mientras se consulta canchas-service.
	 */
	public DisponibilidadResponse consultar(Long canchaId, LocalDate fecha) {
		CanchaRemota cancha = canchasClient.obtenerCancha(canchaId);
		if (!cancha.activa() || Turnos.fueraDeAnticipacion(fecha, anticipacionMaximaDias, reloj)) {
			return new DisponibilidadResponse(canchaId, fecha, List.of());
		}
		ClubRemoto club = canchasClient.obtenerClub(cancha.clubId());
		// Las pendientes ya vencidas no cuentan aunque la tarea programada todavía no las marcó.
		Set<LocalTime> ocupadas = new HashSet<>(reservaRepository.findHorasOcupadas(canchaId, fecha, reloj.instant()));

		List<TurnoLibre> libres = Turnos.delDia(club.horaApertura(), club.horaCierre()).stream()
				.filter(hora -> !ocupadas.contains(hora))
				.filter(hora -> !Turnos.empezo(fecha, hora, reloj))
				.map(hora -> new TurnoLibre(hora, hora.plus(Turnos.DURACION)))
				.toList();
		return new DisponibilidadResponse(canchaId, fecha, libres);
	}

}
