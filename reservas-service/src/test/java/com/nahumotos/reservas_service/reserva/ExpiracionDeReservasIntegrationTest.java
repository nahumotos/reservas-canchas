package com.nahumotos.reservas_service.reserva;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.jayway.jsonpath.JsonPath;
import com.nahumotos.reservas_service.PruebaDeIntegracion;

class ExpiracionDeReservasIntegrationTest extends PruebaDeIntegracion {

	@Autowired
	private ExpiracionDeReservas expiracion;

	@Test
	void expirarVencidas_soloLasVencidas() throws Exception {
		Long a = reservar("18:00"); // vence 12:10
		reloj.avanzar(Duration.ofMinutes(5));
		Long b = reservar("19:00"); // vence 12:15
		reloj.avanzar(Duration.ofMinutes(6)); // son las 12:11

		assertThat(expiracion.expirarVencidas()).isEqualTo(1);
		assertThat(estadoDe(a)).isEqualTo(EstadoReserva.EXPIRADA);
		assertThat(estadoDe(b)).isEqualTo(EstadoReserva.PENDIENTE_PAGO);
	}

	@Test
	void expirarVencidas_liberaElTurno() throws Exception {
		reservar("18:00");
		reloj.avanzar(Duration.ofMinutes(11));
		expiracion.expirarVencidas();

		crear("18:00").andExpect(status().isCreated());
	}

	@Test
	void expirarVencidas_noTocaConfirmadas() throws Exception {
		Long a = reservar("18:00");
		// La confirmación real llega por pago.aprobado; acá sólo importa que esté CONFIRMADA.
		Reserva reserva = reservaRepository.findById(a).orElseThrow();
		reserva.confirmar(reloj.instant());
		reservaRepository.save(reserva);
		reloj.avanzar(Duration.ofMinutes(11));

		assertThat(expiracion.expirarVencidas()).isZero();
		assertThat(estadoDe(a)).isEqualTo(EstadoReserva.CONFIRMADA);
	}

	@Test
	void expirarVencidas_subeLaVersion() throws Exception {
		Long a = reservar("18:00");
		// Copia leída antes de que corra la expiración, como la tendría un pedido en curso.
		Reserva copiaVieja = reservaRepository.findById(a).orElseThrow();
		reloj.avanzar(Duration.ofMinutes(11));
		expiracion.expirarVencidas();

		copiaVieja.cancelar();

		assertThatThrownBy(() -> reservaRepository.save(copiaVieja))
				.isInstanceOf(ObjectOptimisticLockingFailureException.class);
		assertThat(estadoDe(a)).isEqualTo(EstadoReserva.EXPIRADA);
	}

	private Long reservar(String horaInicio) throws Exception {
		String cuerpo = crear(horaInicio).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		return JsonPath.<Integer>read(cuerpo, "$.id").longValue();
	}

	private org.springframework.test.web.servlet.ResultActions crear(String horaInicio) throws Exception {
		return mockMvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content("""
				{"canchaId": 10, "fecha": "2030-06-11", "horaInicio": "%s", "clienteNombre": "Ana", "clienteEmail": "ana@mail.com"}
				""".formatted(horaInicio)));
	}

	private EstadoReserva estadoDe(Long id) {
		return reservaRepository.findById(id).orElseThrow().getEstado();
	}

}
