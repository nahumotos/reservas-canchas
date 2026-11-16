package com.nahumotos.reservas_service;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Reemplaza el Clock de la aplicación por un {@link RelojDeTest} que arranca en
 * {@link PruebaDeIntegracion#AHORA}. Es una clase de primer nivel (no anidada en la base de
 * tests) porque Spring Framework 7.1 va a tomar las @Configuration anidadas como
 * configuración por defecto del test.
 */
@TestConfiguration(proxyBeanMethods = false)
class RelojDeTestConfiguration {

	@Bean
	@Primary
	RelojDeTest relojDeTest() {
		return new RelojDeTest(PruebaDeIntegracion.ZONA, PruebaDeIntegracion.AHORA);
	}

}
