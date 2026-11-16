package com.nahumotos.reservas_service.eventos;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inbox: RabbitMQ entrega cada mensaje "al menos una vez" y nuestro propio outbox puede
 * publicar un evento dos veces, así que los consumidores registran acá cada {@code eventoId}
 * antes de procesarlo.
 */
@Component
public class BandejaDeEntrada {

	private final JdbcClient jdbc;
	private final Clock reloj;

	public BandejaDeEntrada(JdbcClient jdbc, Clock reloj) {
		this.jdbc = jdbc;
		this.reloj = reloj;
	}

	/**
	 * Devuelve true si el evento es nuevo y hay que procesarlo; false si ya se procesó.
	 * MANDATORY: va en la misma transacción que el procesamiento, así un rollback también
	 * deshace el registro y la redelivery lo procesa de nuevo. Si dos entregas del mismo evento
	 * llegan a la vez, la segunda espera en el índice hasta que la primera termina y recibe 0.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean registrar(UUID eventoId, String tipo) {
		int insertadas = jdbc.sql("""
				insert into evento_procesado (evento_id, tipo, procesado_en) values (?, ?, ?)
				on conflict (evento_id) do nothing
				""")
				.params(eventoId, tipo, OffsetDateTime.ofInstant(reloj.instant(), ZoneOffset.UTC))
				.update();
		return insertadas == 1;
	}

}
