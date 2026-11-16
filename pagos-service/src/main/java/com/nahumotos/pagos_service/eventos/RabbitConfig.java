package com.nahumotos.pagos_service.eventos;

import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.json.JsonMapper;

/**
 * Topología de RabbitMQ que le corresponde a pagos-service: el exchange compartido (lo declaran
 * los dos servicios, con los mismos parámetros) y las colas que este servicio consume. Cada
 * consumidor declara sus colas; reservas-service declara la suya.
 */
@Configuration(proxyBeanMethods = false)
public class RabbitConfig {

	public static final String EXCHANGE = "reservas-canchas.eventos";

	/** Adonde van los mensajes que fallaron todos sus reintentos (o que no se pueden leer). */
	public static final String EXCHANGE_DLX = "reservas-canchas.eventos.dlx";

	public static final String COLA_RESERVA_RECHAZADA = "pagos-service.reserva-rechazada";

	public static final String COLA_RESERVA_CANCELADA = "pagos-service.reserva-cancelada";

	@Bean
	Declarables topologiaDeEventos() {
		TopicExchange eventos = ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
		DirectExchange dlx = ExchangeBuilder.directExchange(EXCHANGE_DLX).durable(true).build();
		List<Declarable> declarables = new ArrayList<>(List.of(eventos, dlx));
		declarables.addAll(colaConDlq(COLA_RESERVA_RECHAZADA, ReservaRechazada.TIPO, eventos, dlx));
		declarables.addAll(colaConDlq(COLA_RESERVA_CANCELADA, ReservaCancelada.TIPO, eventos, dlx));
		return new Declarables(declarables);
	}

	/**
	 * Cola durable ligada al exchange de eventos y su DLQ ({@code <cola>.dlq}). Cuando el
	 * listener agota los reintentos, el mensaje se rechaza sin reencolar y RabbitMQ lo manda al
	 * DLX con la routing key {@code <cola>}, que lo deja en la DLQ para revisarlo a mano.
	 */
	private static List<Declarable> colaConDlq(String nombre, String routingKey, TopicExchange eventos,
			DirectExchange dlx) {
		Queue cola = QueueBuilder.durable(nombre)
				.deadLetterExchange(EXCHANGE_DLX)
				.deadLetterRoutingKey(nombre)
				.build();
		Queue dlq = QueueBuilder.durable(nombre + ".dlq").build();
		return List.of(cola, dlq,
				BindingBuilder.bind(cola).to(eventos).with(routingKey),
				BindingBuilder.bind(dlq).to(dlx).with(nombre));
	}

	/**
	 * JSON con Jackson 3 (el mismo JsonMapper que usa la API). El tipo a deserializar sale
	 * siempre del parámetro del @RabbitListener, nunca del header __TypeId__: los eventos los
	 * publica otro servicio con sus propias clases.
	 */
	@Bean
	MessageConverter conversorDeEventos(JsonMapper jsonMapper) {
		JacksonJsonMessageConverter conversor = new JacksonJsonMessageConverter(jsonMapper);
		conversor.setAlwaysConvertToInferredType(true);
		return conversor;
	}

}
