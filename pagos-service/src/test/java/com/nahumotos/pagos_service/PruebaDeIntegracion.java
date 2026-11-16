package com.nahumotos.pagos_service;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nahumotos.pagos_service.mercadopago.MercadoPagoClient;
import com.nahumotos.pagos_service.reservas.ReservasClient;

/**
 * Base de los tests de integración: PostgreSQL y RabbitMQ reales (Testcontainers) y un reloj
 * fijo. Todas las subclases comparten esta misma configuración, así Spring reutiliza un único
 * contexto y un único par de contenedores para toda la suite.
 * <p>
 * Las tareas programadas están apagadas ("-"): los tests llaman a mano a la publicación de
 * eventos y al reintento de reembolsos para que el resultado no dependa del tiempo.
 * Las credenciales de Mercado Pago son de mentira: ningún test sale a internet.
 */
@SpringBootTest(properties = {
		"eventos.publicacion.cron=-",
		"pagos.reembolsos.cron=-",
		"mercadopago.access-token=TEST-token-de-prueba",
		"mercadopago.webhook-secret=secreto-de-prueba" })
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, RelojDeTestConfiguration.class })
public abstract class PruebaDeIntegracion {

	/** 10/06/2030 a las 12:00 en Argentina. */
	public static final Instant AHORA = Instant.parse("2030-06-10T15:00:00Z");

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected JdbcTemplate jdbc;

	@Autowired
	protected RelojDeTest reloj;

	/** Mercado Pago simulado: su contrato HTTP se prueba aparte en MercadoPagoClientTest. */
	@MockitoBean
	protected MercadoPagoClient mercadoPago;

	@MockitoBean
	protected ReservasClient reservasClient;

	@BeforeEach
	void prepararEscenarioBase() {
		jdbc.update("delete from evento_procesado");
		jdbc.update("delete from evento_saliente");
		jdbc.update("delete from pago");
		reloj.fijar(AHORA);
	}

}
