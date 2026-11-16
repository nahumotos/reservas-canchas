package com.nahumotos.pagos_service.eventos;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Fila del outbox (tabla {@code evento_saliente}): un evento esperando ser publicado. */
@Entity
@Table(name = "evento_saliente")
public class EventoSaliente {

	@Id
	private UUID id;

	@Column(name = "tipo", nullable = false, length = 50)
	private String tipo;

	/** El evento completo en JSON, tal cual se publica. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "payload", nullable = false)
	private String payload;

	@Column(name = "ocurrido_en", nullable = false)
	private Instant ocurridoEn;

	/** Null mientras RabbitMQ no confirmó haberlo recibido. */
	@Column(name = "enviado_en")
	private Instant enviadoEn;

	/** Requerido por JPA. */
	protected EventoSaliente() {
	}

	EventoSaliente(UUID id, String tipo, String payload, Instant ocurridoEn) {
		this.id = id;
		this.tipo = tipo;
		this.payload = payload;
		this.ocurridoEn = ocurridoEn;
	}

	void marcarEnviado(Instant ahora) {
		this.enviadoEn = ahora;
	}

	public UUID getId() {
		return id;
	}

	public String getTipo() {
		return tipo;
	}

	public String getPayload() {
		return payload;
	}

	public Instant getOcurridoEn() {
		return ocurridoEn;
	}

	public Instant getEnviadoEn() {
		return enviadoEn;
	}

}
