package com.nahumotos.pagos_service.eventos;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.nahumotos.pagos_service.PruebaDeIntegracion;

class BandejaDeEntradaIntegrationTest extends PruebaDeIntegracion {

	@Autowired
	private BandejaDeEntrada bandejaDeEntrada;

	@Autowired
	private TransactionTemplate transaccion;

	@Test
	void registrar_elMismoEventoDosVeces_soloLaPrimeraEsNuevo() {
		UUID eventoId = UUID.randomUUID();

		Boolean primera = transaccion.execute(estado -> bandejaDeEntrada.registrar(eventoId, "reserva.rechazada"));
		Boolean segunda = transaccion.execute(estado -> bandejaDeEntrada.registrar(eventoId, "reserva.rechazada"));

		assertThat(primera).isTrue();
		assertThat(segunda).isFalse();
		assertThat(jdbc.queryForObject("select count(*) from evento_procesado", Integer.class)).isEqualTo(1);
	}

	@Test
	void registrar_siLaTransaccionHaceRollback_elEventoNoQuedaProcesado() {
		UUID eventoId = UUID.randomUUID();

		transaccion.executeWithoutResult(estado -> {
			bandejaDeEntrada.registrar(eventoId, "reserva.rechazada");
			estado.setRollbackOnly();
		});

		// Si el procesamiento falló, la redelivery tiene que poder procesarlo de nuevo.
		Boolean deNuevo = transaccion.execute(estado -> bandejaDeEntrada.registrar(eventoId, "reserva.rechazada"));
		assertThat(deNuevo).isTrue();
	}

}
