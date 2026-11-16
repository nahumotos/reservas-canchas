package com.nahumotos.reservas_service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Reloj que los tests fijan y adelantan a mano, para que "ahora" sea determinístico. */
public class RelojDeTest extends Clock {

	private final ZoneId zona;
	private volatile Instant instante;

	public RelojDeTest(ZoneId zona, LocalDateTime inicial) {
		this.zona = zona;
		fijar(inicial);
	}

	/** Fija el reloj en una fecha y hora local de la zona del reloj. */
	public void fijar(LocalDateTime local) {
		this.instante = local.atZone(zona).toInstant();
	}

	public void avanzar(Duration duracion) {
		this.instante = instante.plus(duracion);
	}

	@Override
	public ZoneId getZone() {
		return zona;
	}

	@Override
	public Clock withZone(ZoneId otraZona) {
		return Clock.fixed(instante, otraZona);
	}

	@Override
	public Instant instant() {
		return instante;
	}

}
