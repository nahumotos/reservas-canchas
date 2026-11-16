package com.nahumotos.reservas_service.eventos;

import java.time.Instant;
import java.util.UUID;

/**
 * Lo que todo evento de la saga lleva, además de sus datos. Cada servicio tiene sus propios
 * records de eventos (no se comparte código entre servicios); lo único que se comparte es el
 * formato JSON.
 */
public interface Evento {

	/** Identifica el evento; también va como messageId y es la clave del inbox del consumidor. */
	UUID eventoId();

	/** La routing key, p. ej. "reserva.confirmada". */
	String tipo();

	Instant ocurridoEn();

}
