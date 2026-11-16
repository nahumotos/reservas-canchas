package com.nahumotos.pagos_service;

import java.util.TimeZone;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	static {
		// Los tests no pasan por el main de la aplicación, así que repetimos acá lo que hace
		// PagosServiceApplication.main: JVM en UTC para que postgres:18 acepte la conexión.
		// Esta clase se inicializa antes de crear los contenedores, o sea antes de cualquier conexión.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		// Misma versión que docker-compose.yml.
		return new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
	}

	@Bean
	@ServiceConnection
	RabbitMQContainer rabbitContainer() {
		// Misma imagen que docker-compose.yml.
		return new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management"));
	}

}
