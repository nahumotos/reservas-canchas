package com.nahumotos.reservas_service.reserva;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Libera los turnos de las reservas que no se pagaron a tiempo.
 * <p>
 * Es un único UPDATE, así que es idempotente y seguro con varias instancias del servicio:
 * si dos corren a la vez, la segunda no encuentra nada que actualizar.
 */
@Component
public class ExpiracionDeReservas {

	private static final Logger log = LoggerFactory.getLogger(ExpiracionDeReservas.class);

	private final ReservaRepository reservaRepository;
	private final Clock reloj;

	public ExpiracionDeReservas(ReservaRepository reservaRepository, Clock reloj) {
		this.reservaRepository = reservaRepository;
		this.reloj = reloj;
	}

	/** Corre según {@code reservas.expiracion.cron} (cada minuto); devuelve cuántas expiró. */
	@Scheduled(cron = "${reservas.expiracion.cron}")
	@Transactional
	public int expirarVencidas() {
		int expiradas = reservaRepository.expirarVencidas(reloj.instant());
		if (expiradas > 0) {
			log.info("Expiraron {} reservas pendientes de pago", expiradas);
		}
		return expiradas;
	}

}
