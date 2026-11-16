package com.nahumotos.pagos_service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Reloj que los tests fijan y adelantan a mano, para que "ahora" sea determinístico. */
public class RelojDeTest extends Clock {

	private volatile Instant instante;

	public RelojDeTest(Instant inicial) {
		this.instante = inicial;
	}

	public void fijar(Instant nuevo) {
		this.instante = nuevo;
	}

	public void avanzar(Duration duracion) {
		this.instante = instante.plus(duracion);
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
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
