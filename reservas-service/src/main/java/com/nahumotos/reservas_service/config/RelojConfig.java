package com.nahumotos.reservas_service.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class RelojConfig {

	/**
	 * Única fuente de "ahora" de la aplicación, en la zona de los clubes. Inyectarlo (en vez de
	 * llamar a LocalDateTime.now()) evita depender de la zona de la JVM y permite fijarlo en los tests.
	 */
	@Bean
	Clock reloj(ReservasProperties propiedades) {
		return Clock.system(propiedades.zonaHoraria());
	}

}
