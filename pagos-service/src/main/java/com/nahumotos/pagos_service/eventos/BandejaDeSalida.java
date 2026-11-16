package com.nahumotos.pagos_service.eventos;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * Outbox: deja el evento en {@code evento_saliente} para que {@link PublicadorDeEventos} lo
 * publique. No publica nada en RabbitMQ directamente: si lo hiciera dentro de la transacción,
 * un rollback posterior dejaría publicado un evento falso; si lo hiciera después del commit,
 * una caída en el medio lo perdería.
 */
@Component
public class BandejaDeSalida {

	private final EntityManager entityManager;
	private final JsonMapper jsonMapper;

	public BandejaDeSalida(EntityManager entityManager, JsonMapper jsonMapper) {
		this.entityManager = entityManager;
		this.jsonMapper = jsonMapper;
	}

	/** MANDATORY: tiene que ir en la misma transacción que el cambio de estado que lo origina. */
	@Transactional(propagation = Propagation.MANDATORY)
	public void guardar(Evento evento) {
		String payload = jsonMapper.writeValueAsString(evento);
		entityManager.persist(new EventoSaliente(evento.eventoId(), evento.tipo(), payload, evento.ocurridoEn()));
	}

}
