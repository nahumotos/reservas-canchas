package com.nahumotos.reservas_service.eventos;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publica en RabbitMQ los eventos del outbox. Un evento recién se marca como enviado cuando el
 * broker lo confirma (publisher confirms) <b>y</b> llegó a alguna cola; si no, queda pendiente y
 * se reintenta en la próxima pasada. Por eso un evento puede publicarse más de una vez (por
 * ejemplo, si el servicio se cae entre el ack y el commit): los consumidores lo deduplican por
 * {@code eventoId}.
 */
@Component
public class PublicadorDeEventos {

	private static final Logger log = LoggerFactory.getLogger(PublicadorDeEventos.class);

	/** Cuántos eventos se publican por pasada como máximo. */
	private static final int LOTE = 100;

	/** Cuánto esperar el ack del broker por cada evento antes de darlo por no enviado. */
	private static final Duration ESPERA_CONFIRMACION = Duration.ofSeconds(5);

	/** Un evento que no encuentra cola se avisa en el log como mucho una vez por este intervalo. */
	private static final Duration INTERVALO_AVISO_SIN_COLA = Duration.ofMinutes(1);

	private enum Resultado {
		ENVIADO, SIN_COLA, FALLA
	}

	/**
	 * Eventos que todavía no consume nadie: van sin mandatory, porque si no quedarían para siempre
	 * en el outbox. reserva.confirmada la va a consumir notificaciones-service; cuando exista, hay
	 * que sacarla de acá para que tampoco se pueda perder.
	 */
	private static final Set<String> SIN_CONSUMIDOR_TODAVIA = Set.of(ReservaConfirmada.TIPO);

	private final EventoSalienteRepository repositorio;
	private final RabbitTemplate conRuteoObligatorio;
	private final RabbitTemplate sinRuteoObligatorio;
	private final TransactionTemplate transaccion;
	private final Clock reloj;
	private final Map<UUID, Instant> ultimoAvisoSinCola = new ConcurrentHashMap<>();

	public PublicadorDeEventos(EventoSalienteRepository repositorio, ConnectionFactory connectionFactory,
			PlatformTransactionManager transactionManager, Clock reloj) {
		this.repositorio = repositorio;
		// Templates propios. Con publisher confirms RabbitMQ también da ack a un mensaje que no
		// llegó a ninguna cola (por ejemplo, pagos-service todavía no declaró la suya, o RabbitMQ
		// perdió sus datos). Con mandatory el broker lo devuelve y nos enteramos: es lo que hace
		// falta para reserva.rechazada y reserva.cancelada, que terminan en un reembolso.
		this.conRuteoObligatorio = new RabbitTemplate(connectionFactory);
		this.conRuteoObligatorio.setMandatory(true);
		this.sinRuteoObligatorio = new RabbitTemplate(connectionFactory);
		this.transaccion = new TransactionTemplate(transactionManager);
		this.reloj = reloj;
	}

	/** Corre según {@code eventos.publicacion.cron}; devuelve cuántos eventos publicó. */
	@Scheduled(cron = "${eventos.publicacion.cron}")
	public int publicarPendientes() {
		Integer enviados = transaccion.execute(estado -> {
			int publicados = 0;
			for (EventoSaliente evento : repositorio.bloquearPendientes(LOTE)) {
				Resultado resultado = publicar(evento);
				if (resultado == Resultado.FALLA) {
					// Si el broker no confirma uno, lo más probable es que tampoco confirme los
					// siguientes; cortamos y respetamos el orden en la próxima pasada.
					break;
				}
				if (resultado == Resultado.ENVIADO) {
					evento.marcarEnviado(reloj.instant());
					publicados++;
				}
				// SIN_COLA: queda pendiente; los demás pueden ir a otras colas que sí existen.
			}
			return publicados;
		});
		return enviados == null ? 0 : enviados;
	}

	private Resultado publicar(EventoSaliente evento) {
		MessageProperties propiedades = new MessageProperties();
		propiedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
		propiedades.setContentEncoding(StandardCharsets.UTF_8.name());
		propiedades.setMessageId(evento.getId().toString());
		propiedades.setType(evento.getTipo());
		propiedades.setTimestamp(Date.from(evento.getOcurridoEn()));
		propiedades.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
		// Mandamos el JSON tal cual, sin pasar por un MessageConverter: así no viaja el header
		// __TypeId__ con nombres de clases de este servicio.
		Message mensaje = new Message(evento.getPayload().getBytes(StandardCharsets.UTF_8), propiedades);
		CorrelationData correlacion = new CorrelationData(evento.getId().toString());
		try {
			RabbitTemplate rabbitTemplate = SIN_CONSUMIDOR_TODAVIA.contains(evento.getTipo())
					? sinRuteoObligatorio
					: conRuteoObligatorio;
			rabbitTemplate.send(RabbitConfig.EXCHANGE, evento.getTipo(), mensaje, correlacion);
			CorrelationData.Confirm confirmacion = correlacion.getFuture()
					.get(ESPERA_CONFIRMACION.toMillis(), TimeUnit.MILLISECONDS);
			if (!confirmacion.ack()) {
				log.warn("RabbitMQ no aceptó el evento {} ({}): {}", evento.getId(), evento.getTipo(),
						confirmacion.reason());
				return Resultado.FALLA;
			}
			// Spring AMQP garantiza que el mensaje devuelto se registra antes de completar el ack.
			if (correlacion.getReturned() != null) {
				avisarSinCola(evento);
				return Resultado.SIN_COLA;
			}
			ultimoAvisoSinCola.remove(evento.getId());
			return Resultado.ENVIADO;
		}
		catch (AmqpException | ExecutionException | TimeoutException ex) {
			log.warn("No se pudo publicar el evento {} ({}); se reintenta en la próxima pasada: {}", evento.getId(),
					evento.getTipo(), ex.toString());
			return Resultado.FALLA;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return Resultado.FALLA;
		}
	}

	/** El reintento es cada segundo: sin este límite, un consumidor caído llenaría el log. */
	private void avisarSinCola(EventoSaliente evento) {
		Instant ahora = reloj.instant();
		Instant ultimo = ultimoAvisoSinCola.get(evento.getId());
		if (ultimo == null || !ahora.isBefore(ultimo.plus(INTERVALO_AVISO_SIN_COLA))) {
			ultimoAvisoSinCola.put(evento.getId(), ahora);
			log.warn("El evento {} ({}) no llegó a ninguna cola: su consumidor todavía no declaró la cola o "
					+ "RabbitMQ perdió sus datos. Queda pendiente y se reintenta", evento.getId(), evento.getTipo());
		}
	}

}
