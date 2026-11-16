package com.nahumotos.pagos_service.reservas;

import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class ReservasClientConfig {

	/**
	 * Timeouts explícitos: sin ellos, si reservas-service se cuelga, cada pedido de pago queda
	 * esperando indefinidamente y se agotan los hilos de Tomcat.
	 */
	@Bean
	ReservasClient reservasClient(ReservasServiceProperties propiedades) {
		HttpClient httpClient = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(propiedades.timeoutConexion())
				.build();
		var requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(propiedades.timeoutLectura());

		RestClient restClient = RestClient.builder()
				.baseUrl(propiedades.url().toString())
				.requestFactory(requestFactory)
				.build();
		return new ReservasClient(restClient);
	}

}
