package com.nahumotos.reservas_service.reserva;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import com.jayway.jsonpath.JsonPath;
import com.nahumotos.reservas_service.PruebaDeIntegracion;

/**
 * El caso que justifica el índice único parcial: dos pedidos por el mismo turno al mismo
 * tiempo. Va por HTTP real (Tomcat en puerto aleatorio) para que cada pedido corra en su
 * propio hilo de servidor, como en producción.
 */
class ReservaConcurrenciaIntegrationTest extends PruebaDeIntegracion {

	@Value("${local.server.port}")
	private int puerto;

	@Test
	void dosPedidosSimultaneosAlMismoTurno_unoCreaYElOtroRecibe409() throws Exception {
		// Ninguno de los dos pedidos sigue hasta que ambos consultaron la cancha: así los dos
		// pasan las validaciones y llegan juntos al INSERT. Sólo la base puede frenar al segundo.
		CyclicBarrier ambosConsultaronLaCancha = new CyclicBarrier(2);
		when(canchasClient.obtenerCancha(CANCHA_ID)).thenAnswer(invocacion -> {
			ambosConsultaronLaCancha.await(5, SECONDS);
			return cancha(true);
		});

		HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
		HttpRequest pedido = HttpRequest.newBuilder(URI.create("http://localhost:" + puerto + "/api/reservas"))
				.header("Content-Type", "application/json")
				.POST(BodyPublishers.ofString("""
						{"canchaId": 10, "fecha": "2030-06-11", "horaInicio": "18:00",
						 "clienteNombre": "Ana", "clienteEmail": "ana@mail.com"}
						"""))
				.build();

		List<HttpResponse<String>> respuestas;
		try (ExecutorService hilos = Executors.newFixedThreadPool(2)) {
			Future<HttpResponse<String>> primero = hilos.submit(() -> http.send(pedido, BodyHandlers.ofString()));
			Future<HttpResponse<String>> segundo = hilos.submit(() -> http.send(pedido, BodyHandlers.ofString()));
			respuestas = List.of(primero.get(15, SECONDS), segundo.get(15, SECONDS));
		}

		assertThat(respuestas).extracting(HttpResponse::statusCode).containsExactlyInAnyOrder(201, 409);

		String conflicto = respuestas.stream().filter(r -> r.statusCode() == 409).findFirst().orElseThrow().body();
		assertThat(JsonPath.<String>read(conflicto, "$.title")).isEqualTo("Conflicto");
		assertThat(JsonPath.<String>read(conflicto, "$.detail"))
				.isEqualTo("El turno del 2030-06-11 a las 18:00 ya está reservado");

		Integer reservasVivas = jdbc.queryForObject("""
				select count(*) from reserva
				where cancha_id = 10 and fecha = date '2030-06-11' and hora_inicio = time '18:00'
				  and estado = 'PENDIENTE_PAGO'
				""", Integer.class);
		assertThat(reservasVivas).isEqualTo(1);
	}

}
