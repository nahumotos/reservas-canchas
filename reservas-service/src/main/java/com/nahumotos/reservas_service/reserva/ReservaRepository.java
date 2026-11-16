package com.nahumotos.reservas_service.reserva;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

	List<Reserva> findByClienteEmailOrderByFechaDescHoraInicioDesc(String clienteEmail);

	/**
	 * Horas de inicio realmente ocupadas de una cancha en una fecha: las confirmadas y las
	 * pendientes que todavía no vencieron. Una pendiente vencida que la tarea programada aún
	 * no marcó como EXPIRADA no cuenta (el alta la expira antes de insertar).
	 */
	@Query("""
			select r.horaInicio from Reserva r
			where r.canchaId = :canchaId and r.fecha = :fecha
			  and (r.estado = com.nahumotos.reservas_service.reserva.EstadoReserva.CONFIRMADA
			       or (r.estado = com.nahumotos.reservas_service.reserva.EstadoReserva.PENDIENTE_PAGO
			           and r.venceEn > :ahora))
			""")
	List<LocalTime> findHorasOcupadas(Long canchaId, LocalDate fecha, Instant ahora);

	/**
	 * Pasa a EXPIRADA todas las PENDIENTE_PAGO vencidas en un solo UPDATE.
	 * Sube la versión a mano (un UPDATE masivo no pasa por @Version) para que un pedido que
	 * leyó la reserva antes, y quiera confirmarla o cancelarla, falle con locking optimista
	 * en vez de pisar el EXPIRADA.
	 */
	@Modifying
	@Query("""
			update Reserva r
			set r.estado = com.nahumotos.reservas_service.reserva.EstadoReserva.EXPIRADA, r.version = r.version + 1
			where r.estado = com.nahumotos.reservas_service.reserva.EstadoReserva.PENDIENTE_PAGO
			  and r.venceEn <= :ahora
			""")
	int expirarVencidas(Instant ahora);

	/**
	 * Igual que {@link #expirarVencidas(Instant)} pero sólo para un turno. Lo usa el alta justo
	 * antes del INSERT, para que una pendiente vencida no siga bloqueando el índice único.
	 */
	@Modifying
	@Query("""
			update Reserva r
			set r.estado = com.nahumotos.reservas_service.reserva.EstadoReserva.EXPIRADA, r.version = r.version + 1
			where r.canchaId = :canchaId and r.fecha = :fecha and r.horaInicio = :horaInicio
			  and r.estado = com.nahumotos.reservas_service.reserva.EstadoReserva.PENDIENTE_PAGO
			  and r.venceEn <= :ahora
			""")
	int expirarVencidasDelTurno(Long canchaId, LocalDate fecha, LocalTime horaInicio, Instant ahora);

}
