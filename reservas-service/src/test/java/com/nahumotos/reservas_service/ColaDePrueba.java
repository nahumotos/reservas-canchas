package com.nahumotos.reservas_service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.nahumotos.reservas_service.eventos.RabbitConfig;

/**
 * Cola temporal ligada al exchange de eventos, para ver desde un test lo que este servicio
 * publica (reserva.confirmada, reserva.rechazada…; los consume pagos-service, que acá no
 * corre). Se vacía al crearla.
 */
public final class ColaDePrueba {

	private final RabbitTemplate rabbit;
	private final String nombre;

	private ColaDePrueba(RabbitTemplate rabbit, String nombre) {
		this.rabbit = rabbit;
		this.nombre = nombre;
	}

	public static ColaDePrueba ligadaA(String routingKey, AmqpAdmin admin, RabbitTemplate rabbit) {
		String nombre = "prueba." + routingKey;
		// Durable porque RabbitMQ 4 ya no acepta colas transitorias no exclusivas; el contenedor
		// se descarta al terminar la suite, así que no queda basura.
		Queue cola = new Queue(nombre, true, false, false);
		admin.declareQueue(cola);
		admin.declareBinding(BindingBuilder.bind(cola).to(new TopicExchange(RabbitConfig.EXCHANGE)).with(routingKey));
		admin.purgeQueue(nombre, false);
		return new ColaDePrueba(rabbit, nombre);
	}

	/**
	 * Borra la cola de prueba de esa routing key, si existe. Las colas de prueba son durables y
	 * sobreviven entre tests: hace falta para simular que el consumidor todavía no declaró la suya.
	 */
	public static void borrar(String routingKey, AmqpAdmin admin) {
		admin.deleteQueue("prueba." + routingKey);
	}

	/** El próximo mensaje; falla si no llega en 5 segundos. */
	public Message recibir() {
		Message mensaje = rabbit.receive(nombre, Duration.ofSeconds(5).toMillis());
		assertThat(mensaje).as("mensaje en la cola " + nombre).isNotNull();
		return mensaje;
	}

	/** El próximo mensaje si llega en medio segundo, o null. Para comprobar que no hay más. */
	public Message recibirSiHay() {
		return rabbit.receive(nombre, 500);
	}

}
