package com.nahumotos.canchas_service.club;

import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Mapea la tabla {@code club} de la migración V1. */
@Entity
@Table(name = "club")
public class Club {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "nombre", nullable = false, length = 100)
	private String nombre;

	@Column(name = "direccion", nullable = false, length = 200)
	private String direccion;

	@Column(name = "hora_apertura", nullable = false)
	private LocalTime horaApertura;

	@Column(name = "hora_cierre", nullable = false)
	private LocalTime horaCierre;

	/** Requerido por JPA. */
	protected Club() {
	}

	public Club(String nombre, String direccion, LocalTime horaApertura, LocalTime horaCierre) {
		this.nombre = nombre;
		this.direccion = direccion;
		this.horaApertura = horaApertura;
		this.horaCierre = horaCierre;
	}

	public Long getId() {
		return id;
	}

	public String getNombre() {
		return nombre;
	}

	public String getDireccion() {
		return direccion;
	}

	public LocalTime getHoraApertura() {
		return horaApertura;
	}

	public LocalTime getHoraCierre() {
		return horaCierre;
	}

}
