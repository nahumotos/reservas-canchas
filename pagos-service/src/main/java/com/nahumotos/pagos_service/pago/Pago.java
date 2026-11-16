package com.nahumotos.pagos_service.pago;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Mapea la tabla {@code pago} de la migración V1. */
@Entity
@Table(name = "pago")
public class Pago {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "reserva_id", nullable = false)
	private Long reservaId;

	@Column(name = "monto", nullable = false, precision = 10, scale = 2)
	private BigDecimal monto;

	@Enumerated(EnumType.STRING)
	@Column(name = "estado", nullable = false, length = 20)
	private EstadoPago estado;

	@Column(name = "mp_order_id", unique = true, length = 64)
	private String mpOrderId;

	@Column(name = "checkout_url", length = 500)
	private String checkoutUrl;

	@Column(name = "clave_idempotencia", nullable = false, unique = true)
	private UUID claveIdempotencia;

	@Column(name = "creado_en", nullable = false)
	private Instant creadoEn;

	@Column(name = "actualizado_en", nullable = false)
	private Instant actualizadoEn;

	/** Dos notificaciones simultáneas de la misma order no pueden aplicar el mismo cambio dos veces. */
	@Version
	@Column(name = "version", nullable = false)
	private Long version;

	/** Requerido por JPA. */
	protected Pago() {
	}

	/** Un pago nuevo queda PENDIENTE y sin order hasta que Mercado Pago la crea. */
	public Pago(Long reservaId, BigDecimal monto, UUID claveIdempotencia, Instant ahora) {
		this.reservaId = reservaId;
		this.monto = monto;
		this.claveIdempotencia = claveIdempotencia;
		this.estado = EstadoPago.PENDIENTE;
		this.creadoEn = ahora;
		this.actualizadoEn = ahora;
	}

	public void asociarOrder(String mpOrderId, String checkoutUrl, Instant ahora) {
		this.mpOrderId = mpOrderId;
		this.checkoutUrl = checkoutUrl;
		this.actualizadoEn = ahora;
	}

	public boolean tieneOrder() {
		return mpOrderId != null;
	}

	/**
	 * Aplica el estado que tiene la order en Mercado Pago. Devuelve true sólo cuando el pago
	 * pasa a APROBADO en esta llamada: es lo que dispara pago.aprobado, así una notificación
	 * repetida no genera un segundo evento.
	 * <p>
	 * RECHAZADO y EXPIRADO sólo se alcanzan desde PENDIENTE (una notificación vieja que llega
	 * tarde no deshace un estado final). APROBADO, además, desde RECHAZADO o EXPIRADO: una order
	 * que terminó cobrada nunca se ignora, porque sería plata cobrada sin reserva ni reembolso;
	 * reservas-service decide si confirma o rechaza. REEMBOLSADO desde cualquier estado en el que
	 * se haya cobrado (o se haya intentado devolver): puede venir de nuestro propio reembolso o de
	 * uno hecho a mano desde el panel.
	 */
	public boolean registrarEstadoDeOrder(EstadoPago nuevo, Instant ahora) {
		boolean aplica = switch (nuevo) {
			case APROBADO -> estado == EstadoPago.PENDIENTE || estado == EstadoPago.RECHAZADO
					|| estado == EstadoPago.EXPIRADO;
			case RECHAZADO, EXPIRADO -> estado == EstadoPago.PENDIENTE;
			case REEMBOLSADO -> estado == EstadoPago.PENDIENTE || estado == EstadoPago.APROBADO
					|| estado == EstadoPago.REEMBOLSO_PENDIENTE || estado == EstadoPago.REEMBOLSO_FALLIDO;
			case PENDIENTE, REEMBOLSO_PENDIENTE, REEMBOLSO_FALLIDO -> false;
		};
		if (!aplica) {
			return false;
		}
		estado = nuevo;
		actualizadoEn = ahora;
		return nuevo == EstadoPago.APROBADO;
	}

	/**
	 * Hay que devolver la plata (la reserva se rechazó o se canceló). Sólo un pago APROBADO tiene
	 * algo que reembolsar; uno que ya está en REEMBOLSO_PENDIENTE sigue para reembolsar. Devuelve
	 * true si hay que pedirle el reembolso a Mercado Pago.
	 */
	public boolean solicitarReembolso(Instant ahora) {
		if (estado == EstadoPago.APROBADO) {
			estado = EstadoPago.REEMBOLSO_PENDIENTE;
			actualizadoEn = ahora;
		}
		return estado == EstadoPago.REEMBOLSO_PENDIENTE;
	}

	/**
	 * Una order RECHAZADA o EXPIRADA terminó cobrada, pero la reserva ya tiene otro pago en curso
	 * o aprobado (ux_pago_reserva_activo no deja aprobar este): el cobro sobra y se devuelve.
	 */
	public void registrarCobroSobrante(Instant ahora) {
		if (estado == EstadoPago.RECHAZADO || estado == EstadoPago.EXPIRADO) {
			estado = EstadoPago.REEMBOLSO_PENDIENTE;
			actualizadoEn = ahora;
		}
	}

	/** Mercado Pago rechazó el reembolso con un error permanente: deja de reintentarse solo. */
	public void marcarReembolsoFallido(Instant ahora) {
		if (estado == EstadoPago.REEMBOLSO_PENDIENTE) {
			estado = EstadoPago.REEMBOLSO_FALLIDO;
			actualizadoEn = ahora;
		}
	}

	/** Mercado Pago confirmó el reembolso. */
	public void marcarReembolsado(Instant ahora) {
		if (estado == EstadoPago.REEMBOLSO_PENDIENTE) {
			estado = EstadoPago.REEMBOLSADO;
			actualizadoEn = ahora;
		}
	}

	/**
	 * X-Idempotency-Key del reembolso: fija por pago (se deriva de la clave de creación, así no
	 * hace falta otra columna) y distinta de ella. Cualquier reintento, del proceso que sea, manda
	 * la misma clave y Mercado Pago no reembolsa dos veces.
	 */
	public UUID claveDeReembolso() {
		return UUID.nameUUIDFromBytes(("reembolso:" + claveIdempotencia).getBytes(StandardCharsets.UTF_8));
	}

	public Long getId() {
		return id;
	}

	public Long getReservaId() {
		return reservaId;
	}

	public BigDecimal getMonto() {
		return monto;
	}

	public EstadoPago getEstado() {
		return estado;
	}

	public String getMpOrderId() {
		return mpOrderId;
	}

	public String getCheckoutUrl() {
		return checkoutUrl;
	}

	public UUID getClaveIdempotencia() {
		return claveIdempotencia;
	}

	public Instant getCreadoEn() {
		return creadoEn;
	}

	public Instant getActualizadoEn() {
		return actualizadoEn;
	}

	public Long getVersion() {
		return version;
	}

}
