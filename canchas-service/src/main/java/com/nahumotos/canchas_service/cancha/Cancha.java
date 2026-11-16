package com.nahumotos.canchas_service.cancha;

import java.math.BigDecimal;

import com.nahumotos.canchas_service.club.Club;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Mapea la tabla {@code cancha} de la migración V1. */
@Entity
@Table(name = "cancha", uniqueConstraints = @UniqueConstraint(columnNames = { "club_id", "nombre" }))
public class Cancha {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "club_id", nullable = false)
	private Club club;

	@Column(name = "nombre", nullable = false, length = 50)
	private String nombre;

	@Enumerated(EnumType.STRING)
	@Column(name = "deporte", nullable = false, length = 20)
	private Deporte deporte;

	@Column(name = "precio_hora", nullable = false, precision = 10, scale = 2)
	private BigDecimal precioHora;

	@Column(name = "activa", nullable = false)
	private boolean activa = true;

	/** Requerido por JPA. */
	protected Cancha() {
	}

	/** Una cancha nueva nace activa, igual que el DEFAULT TRUE de la tabla. */
	public Cancha(Club club, String nombre, Deporte deporte, BigDecimal precioHora) {
		this.club = club;
		this.nombre = nombre;
		this.deporte = deporte;
		this.precioHora = precioHora;
	}

	public Long getId() {
		return id;
	}

	public Club getClub() {
		return club;
	}

	public String getNombre() {
		return nombre;
	}

	public Deporte getDeporte() {
		return deporte;
	}

	public BigDecimal getPrecioHora() {
		return precioHora;
	}

	public boolean isActiva() {
		return activa;
	}

}
