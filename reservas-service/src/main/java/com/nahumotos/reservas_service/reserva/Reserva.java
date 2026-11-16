package com.nahumotos.reservas_service.reserva;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import com.nahumotos.reservas_service.error.ConflictoException;
import com.nahumotos.reservas_service.turno.Turnos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Mapea la tabla {@code reserva} de la migración V1. */
@Entity
@Table(name = "reserva")
public class Reserva {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "cancha_id", nullable = false)
	private Long canchaId;

	@Column(name = "club_id", nullable = false)
	private Long clubId;

	@Column(name = "fecha", nullable = false)
	private LocalDate fecha;

	@Column(name = "hora_inicio", nullable = false)
	private LocalTime horaInicio;

	@Column(name = "hora_fin", nullable = false)
	private LocalTime horaFin;

	@Column(name = "cliente_nombre", nullable = false, length = 100)
	private String clienteNombre;

	@Column(name = "cliente_email", nullable = false, length = 200)
	private String clienteEmail;

	/** Precio de la cancha al momento de reservar (V2); no cambia si después cambia el precio. */
	@Column(name = "monto", nullable = false, precision = 10, scale = 2)
	private BigDecimal monto;

	@Enumerated(EnumType.STRING)
	@Column(name = "estado", nullable = false, length = 20)
	private EstadoReserva estado;

	@Column(name = "vence_en", nullable = false)
	private Instant venceEn;

	@Column(name = "creada_en", nullable = false)
	private Instant creadaEn;

	@Version
	@Column(name = "version", nullable = false)
	private Long version;

	/** Requerido por JPA. */
	protected Reserva() {
	}

	/** Una reserva nueva queda pendiente de pago hasta {@code venceEn}. */
	public Reserva(Long canchaId, Long clubId, LocalDate fecha, LocalTime horaInicio, String clienteNombre,
			String clienteEmail, BigDecimal monto, Instant creadaEn, Instant venceEn) {
		this.canchaId = canchaId;
		this.clubId = clubId;
		this.fecha = fecha;
		this.horaInicio = horaInicio;
		this.horaFin = horaInicio.plus(Turnos.DURACION);
		this.clienteNombre = clienteNombre;
		this.clienteEmail = clienteEmail;
		this.monto = monto;
		this.estado = EstadoReserva.PENDIENTE_PAGO;
		this.creadaEn = creadaEn;
		this.venceEn = venceEn;
	}

	/**
	 * Pago aprobado. Confirmar una reserva ya confirmada no hace nada, para que un reintento
	 * del pago no sea un error. Una pendiente cuyo vencimiento ya pasó no se confirma aunque
	 * la tarea de expiración todavía no la haya marcado.
	 */
	public void confirmar(Instant ahora) {
		switch (estado) {
			case CONFIRMADA -> {
			}
			case PENDIENTE_PAGO -> {
				if (vencida(ahora)) {
					throw new ConflictoException("La reserva venció y ya no se puede confirmar");
				}
				estado = EstadoReserva.CONFIRMADA;
			}
			case CANCELADA -> throw new ConflictoException("La reserva está cancelada y no se puede confirmar");
			case EXPIRADA -> throw new ConflictoException("La reserva venció y ya no se puede confirmar");
		}
	}

	/**
	 * Por qué un pago aprobado no puede confirmar esta reserva, o vacío si puede. Sirve para
	 * decidir antes de llamar a {@link #confirmar(Instant)}: así el que procesa el pago no tiene
	 * que atrapar una excepción que deja su transacción marcada para rollback (y perdería el
	 * evento de rechazo que guarda en esa misma transacción).
	 * <p>
	 * A diferencia de {@code confirmar}, una reserva ya CONFIRMADA no se acepta: cada pago se
	 * informa una sola vez (el inbox descarta los repetidos), así que otro pago aprobado para la
	 * misma reserva es un cobro de más y hay que devolverlo.
	 */
	public Optional<String> impedimentoParaConfirmar(Instant ahora) {
		return switch (estado) {
			case PENDIENTE_PAGO -> vencida(ahora)
					? Optional.of("La reserva venció antes de que se aprobara el pago")
					: Optional.empty();
			case EXPIRADA -> Optional.of("La reserva expiró antes de que se aprobara el pago");
			case CANCELADA -> Optional.of("La reserva fue cancelada");
			case CONFIRMADA -> Optional.of("La reserva ya estaba confirmada con otro pago");
		};
	}

	/** Libera el turno. Cancelar una reserva ya cancelada no hace nada. */
	public void cancelar() {
		switch (estado) {
			case PENDIENTE_PAGO, CONFIRMADA -> estado = EstadoReserva.CANCELADA;
			case CANCELADA -> {
			}
			case EXPIRADA -> throw new ConflictoException("La reserva expiró y no se puede cancelar");
		}
	}

	/** Mismo criterio que la tarea de expiración (vence_en &lt;= ahora). */
	private boolean vencida(Instant ahora) {
		return !ahora.isBefore(venceEn);
	}

	public Long getId() {
		return id;
	}

	public Long getCanchaId() {
		return canchaId;
	}

	public Long getClubId() {
		return clubId;
	}

	public LocalDate getFecha() {
		return fecha;
	}

	public LocalTime getHoraInicio() {
		return horaInicio;
	}

	public LocalTime getHoraFin() {
		return horaFin;
	}

	public String getClienteNombre() {
		return clienteNombre;
	}

	public String getClienteEmail() {
		return clienteEmail;
	}

	public BigDecimal getMonto() {
		return monto;
	}

	public EstadoReserva getEstado() {
		return estado;
	}

	public Instant getVenceEn() {
		return venceEn;
	}

	public Instant getCreadaEn() {
		return creadaEn;
	}

	public Long getVersion() {
		return version;
	}

}
