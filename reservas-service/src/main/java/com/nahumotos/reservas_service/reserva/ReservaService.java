package com.nahumotos.reservas_service.reserva;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.nahumotos.reservas_service.canchas.CanchaRemota;
import com.nahumotos.reservas_service.canchas.CanchasClient;
import com.nahumotos.reservas_service.canchas.ClubRemoto;
import com.nahumotos.reservas_service.config.ReservasProperties;
import com.nahumotos.reservas_service.error.ConflictoException;
import com.nahumotos.reservas_service.error.RecursoNoEncontradoException;
import com.nahumotos.reservas_service.error.SolicitudInvalidaException;
import com.nahumotos.reservas_service.eventos.BandejaDeSalida;
import com.nahumotos.reservas_service.eventos.ReservaCancelada;
import com.nahumotos.reservas_service.turno.Turnos;

@Service
public class ReservaService {

	/** Nombre del índice único parcial de la migración V1. */
	private static final String INDICE_TURNO_ACTIVO = "ux_reserva_turno_activo";

	private final ReservaRepository reservaRepository;
	private final CanchasClient canchasClient;
	private final BandejaDeSalida bandejaDeSalida;
	private final Clock reloj;
	private final TransactionTemplate transaccion;
	private final Duration vencimientoPago;
	private final int anticipacionMaximaDias;
	private final Duration anticipacionMinimaParaCancelar;

	public ReservaService(ReservaRepository reservaRepository, CanchasClient canchasClient,
			BandejaDeSalida bandejaDeSalida, Clock reloj, PlatformTransactionManager transactionManager,
			ReservasProperties propiedades) {
		this.reservaRepository = reservaRepository;
		this.canchasClient = canchasClient;
		this.bandejaDeSalida = bandejaDeSalida;
		this.reloj = reloj;
		this.transaccion = new TransactionTemplate(transactionManager);
		this.vencimientoPago = propiedades.vencimientoPago();
		this.anticipacionMaximaDias = propiedades.anticipacionMaximaDias();
		this.anticipacionMinimaParaCancelar = propiedades.cancelacion().anticipacionMinima();
	}

	/**
	 * Sin @Transactional a propósito: las llamadas a canchas-service no deben retener una
	 * conexión a la base mientras esperan. Sólo la parte de base (liberar el turno si lo
	 * ocupa una pendiente vencida + INSERT) corre en una transacción.
	 */
	public ReservaResponse crear(CrearReservaRequest request) {
		CanchaRemota cancha = canchasClient.obtenerCancha(request.canchaId());
		if (!cancha.activa()) {
			throw new ConflictoException("La cancha " + request.canchaId() + " no está activa");
		}
		ClubRemoto club = canchasClient.obtenerClub(cancha.clubId());
		LocalDate fecha = request.fecha();
		LocalTime horaInicio = request.horaInicio();
		validarTurno(fecha, horaInicio, club);

		Instant ahora = reloj.instant();
		// El plazo para pagar nunca pasa del inicio del turno: reservar a las 11:55 el turno
		// de las 12:00 vence a las 12:00, no a las 12:05.
		Instant venceEn = primero(ahora.plus(vencimientoPago), Turnos.inicio(fecha, horaInicio, reloj.getZone()));
		// El monto queda congelado con el precio de este momento.
		var reserva = new Reserva(request.canchaId(), cancha.clubId(), fecha, horaInicio, request.clienteNombre(),
				normalizarEmail(request.clienteEmail()), cancha.precioHora(), ahora, venceEn);
		try {
			return transaccion.execute(estado -> {
				// Una pendiente vencida que la tarea programada todavía no marcó sigue ocupando el
				// índice único; la expiramos acá para no rechazar un turno que en realidad está libre.
				reservaRepository.expirarVencidasDelTurno(request.canchaId(), fecha, horaInicio, ahora);
				return ReservaResponse.desde(reservaRepository.saveAndFlush(reserva));
			});
		}
		catch (DataIntegrityViolationException ex) {
			// No hay chequeo previo de "turno libre": dos pedidos simultáneos lo pasarían igual.
			// El índice único parcial es la única fuente de verdad; acá sólo traducimos su violación.
			if (violaIndiceDeTurnoActivo(ex)) {
				throw new ConflictoException("El turno del %s a las %s ya está reservado".formatted(fecha, horaInicio));
			}
			throw ex;
		}
	}

	@Transactional(readOnly = true)
	public ReservaResponse obtener(Long id) {
		return ReservaResponse.desde(buscar(id));
	}

	@Transactional(readOnly = true)
	public List<ReservaResponse> listarPorCliente(String clienteEmail) {
		return reservaRepository.findByClienteEmailOrderByFechaDescHoraInicioDesc(normalizarEmail(clienteEmail))
				.stream()
				.map(ReservaResponse::desde)
				.toList();
	}

	/**
	 * Una PENDIENTE_PAGO se cancela sin más (si el pago llega después, se rechaza y se
	 * reembolsa). Una CONFIRMADA ya está pagada: sólo se cancela con la anticipación mínima y,
	 * en la misma transacción, se deja reserva.cancelada para que pagos-service devuelva la plata.
	 */
	@Transactional
	public ReservaResponse cancelar(Long id) {
		Reserva reserva = buscar(id);
		if (reserva.getEstado() != EstadoReserva.CONFIRMADA) {
			reserva.cancelar();
			return ReservaResponse.desde(reserva);
		}
		Instant ahora = reloj.instant();
		Instant inicioDelTurno = Turnos.inicio(reserva.getFecha(), reserva.getHoraInicio(), reloj.getZone());
		if (Duration.between(ahora, inicioDelTurno).compareTo(anticipacionMinimaParaCancelar) < 0) {
			throw new ConflictoException("Las reservas confirmadas se pueden cancelar hasta %d horas antes del turno"
					.formatted(anticipacionMinimaParaCancelar.toHours()));
		}
		reserva.cancelar();
		bandejaDeSalida.guardar(ReservaCancelada.de(reserva.getId(), ahora));
		return ReservaResponse.desde(reserva);
	}

	private Reserva buscar(Long id) {
		return reservaRepository.findById(id)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe la reserva con id " + id));
	}

	private void validarTurno(LocalDate fecha, LocalTime horaInicio, ClubRemoto club) {
		if (!Turnos.empiezaEnPunto(horaInicio)) {
			throw new SolicitudInvalidaException("Los turnos empiezan en punto: " + horaInicio + " no es válido");
		}
		if (!Turnos.delDia(club.horaApertura(), club.horaCierre()).contains(horaInicio)) {
			throw new SolicitudInvalidaException("El turno de las %s está fuera del horario del club (%s a %s)"
					.formatted(horaInicio, club.horaApertura(), club.horaCierre()));
		}
		if (Turnos.empezo(fecha, horaInicio, reloj)) {
			throw new SolicitudInvalidaException(
					"El turno del %s a las %s ya empezó o está en el pasado".formatted(fecha, horaInicio));
		}
		if (Turnos.fueraDeAnticipacion(fecha, anticipacionMaximaDias, reloj)) {
			throw new SolicitudInvalidaException("Solo se puede reservar con hasta %d días de anticipación (último día: %s)"
					.formatted(anticipacionMaximaDias, Turnos.ultimoDiaReservable(anticipacionMaximaDias, reloj)));
		}
	}

	private static Instant primero(Instant uno, Instant otro) {
		return uno.isBefore(otro) ? uno : otro;
	}

	/** Sólo el índice de turnos es un 409; cualquier otra violación (un CHECK, por ejemplo) es un bug. */
	private static boolean violaIndiceDeTurnoActivo(DataIntegrityViolationException ex) {
		for (Throwable causa = ex; causa != null; causa = causa.getCause()) {
			if (causa instanceof ConstraintViolationException violacion
					&& INDICE_TURNO_ACTIVO.equalsIgnoreCase(violacion.getConstraintName())) {
				return true;
			}
		}
		return false;
	}

	/** Los emails no distinguen mayúsculas: se guardan y se buscan en minúsculas. */
	private static String normalizarEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

}
