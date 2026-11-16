package com.nahumotos.pagos_service.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class RelojConfig {

	/**
	 * Única fuente de "ahora" de la aplicación. Pagos sólo maneja instantes (vencimientos,
	 * auditoría), así que alcanza con UTC. Inyectarlo permite fijarlo en los tests.
	 */
	@Bean
	Clock reloj() {
		return Clock.systemUTC();
	}

}
