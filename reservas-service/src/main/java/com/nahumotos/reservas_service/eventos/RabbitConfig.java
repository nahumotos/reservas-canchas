package com.nahumotos.reservas_service.eventos;

import org.springframework.amqp.core.BindingBuilder;
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
 * Topología de RabbitMQ que le corresponde a reservas-service: el exchange compartido (lo
 * declaran los dos servicios, con los mismos parámetros) y la cola que este servicio consume.
 * Las colas de reserva.rechazada y reserva.cancelada las declara pagos-service, que es quien
 * las consume; reserva.confirmada no tiene consumidor todavía.
 */
@Configuration(proxyBeanMethods = false)
public class RabbitConfig {

	public static final String EXCHANGE = "reservas-canchas.eventos";

	/** Adonde van los mensajes que fallaron todos sus reintentos (o que no se pueden leer). */
	public static final String EXCHANGE_DLX = "reservas-canchas.eventos.dlx";

	public static final String COLA_PAGO_APROBADO = "reservas-service.pago-aprobado";

	/**
	 * Cola durable ligada a pago.aprobado y su DLQ ({@code <cola>.dlq}). Cuando el listener agota
	 * los reintentos, el mensaje se rechaza sin reencolar y RabbitMQ lo manda al DLX con la
	 * routing key {@code <cola>}, que lo deja en la DLQ para revisarlo a mano.
	 */
	@Bean
	Declarables topologiaDeEventos() {
		TopicExchange eventos = ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
		DirectExchange dlx = ExchangeBuilder.directExchange(EXCHANGE_DLX).durable(true).build();
		Queue cola = QueueBuilder.durable(COLA_PAGO_APROBADO)
				.deadLetterExchange(EXCHANGE_DLX)
				.deadLetterRoutingKey(COLA_PAGO_APROBADO)
				.build();
		Queue dlq = QueueBuilder.durable(COLA_PAGO_APROBADO + ".dlq").build();
		return new Declarables(eventos, dlx, cola, dlq,
				BindingBuilder.bind(cola).to(eventos).with(PagoAprobado.TIPO),
				BindingBuilder.bind(dlq).to(dlx).with(COLA_PAGO_APROBADO));
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
