package com.nahumotos.pagos_service.mercadopago;

import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class MercadoPagoClientConfig {

	/**
	 * Timeouts explícitos: si Mercado Pago se cuelga, el pedido de pago o el webhook no quedan
	 * esperando indefinidamente (Mercado Pago corta el webhook a los 22 segundos).
	 */
	@Bean
	MercadoPagoClient mercadoPagoClient(MercadoPagoProperties propiedades) {
		HttpClient httpClient = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(propiedades.timeoutConexion())
				.build();
		var requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(propiedades.timeoutLectura());

		RestClient restClient = RestClient.builder()
				.baseUrl(propiedades.urlBase().toString())
				.requestFactory(requestFactory)
				.build();
		return new MercadoPagoClient(restClient, propiedades.accessToken());
	}

	@Bean
	FirmaDeMercadoPago firmaDeMercadoPago(MercadoPagoProperties propiedades) {
		return new FirmaDeMercadoPago(propiedades.webhookSecret());
	}

}
