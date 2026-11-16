package com.nahumotos.pagos_service.mercadopago;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

/**
 * Sin credenciales de Mercado Pago el servicio no tiene que arrancar, y el error tiene que
 * decir qué variable falta. El caso "vacía" es el de un .env copiado de .env.example.
 */
class MercadoPagoPropertiesTest {

	private final ApplicationContextRunner contexto = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
			.withUserConfiguration(ConPropiedades.class)
			.withPropertyValues(
					"mercadopago.url-base=https://api.mercadopago.com",
					"mercadopago.timeout-conexion=2s",
					"mercadopago.timeout-lectura=5s");

	@Test
	void conAmbasCredenciales_arranca() {
		contexto.withPropertyValues("mercadopago.access-token=TEST-123", "mercadopago.webhook-secret=secreto")
				.run(ctx -> assertThat(ctx).hasNotFailed()
						.getBean(MercadoPagoProperties.class)
						.extracting(MercadoPagoProperties::accessToken)
						.isEqualTo("TEST-123"));
	}

	@Test
	void sinAccessToken_noArrancaYNombraLaVariable() {
		contexto.withPropertyValues("mercadopago.webhook-secret=secreto")
				.run(ctx -> assertThat(ctx).hasFailed()
						.getFailure().rootCause().hasMessageContaining("MP_ACCESS_TOKEN"));
	}

	@Test
	void webhookSecretVacio_noArrancaYNombraLaVariable() {
		contexto.withPropertyValues("mercadopago.access-token=TEST-123", "mercadopago.webhook-secret=")
				.run(ctx -> assertThat(ctx).hasFailed()
						.getFailure().rootCause().hasMessageContaining("MP_WEBHOOK_SECRET"));
	}

	@EnableConfigurationProperties(MercadoPagoProperties.class)
	static class ConPropiedades {
	}

}
