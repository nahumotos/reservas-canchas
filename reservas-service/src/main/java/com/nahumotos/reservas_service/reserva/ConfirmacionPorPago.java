package com.nahumotos.reservas_service.reserva;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.nahumotos.reservas_service.eventos.BandejaDeEntrada;
import com.nahumotos.reservas_service.eventos.BandejaDeSalida;
import com.nahumotos.reservas_service.eventos.PagoAprobado;
import com.nahumotos.reservas_service.eventos.RabbitConfig;
import com.nahumotos.reservas_service.eventos.ReservaConfirmada;
import com.nahumotos.reservas_service.eventos.ReservaRechazada;

/**
 * Paso de la saga que le toca a reservas-service: cuando pagos-service avisa que se aprobó un
 * pago, la reserva se confirma (reserva.confirmada) o, si ya no se puede, se rechaza
 * (reserva.rechazada) para que pagos-service devuelva la plata.
 * <p>
 * Todo en una transacción: el registro en el inbox, el cambio de estado y el evento de salida
 * quedan juntos o no queda ninguno. Si algo falla (por ejemplo, la tarea de expiración modificó
 * la reserva al mismo tiempo y salta el locking optimista), el mensaje se reintenta y la
 * reserva se vuelve a evaluar con su estado nuevo.
 */
@Component
public class ConfirmacionPorPago {

	private static final Logger log = LoggerFactory.getLogger(ConfirmacionPorPago.class);

	private final ReservaRepository reservaRepository;
	private final BandejaDeEntrada bandejaDeEntrada;
	private final BandejaDeSalida bandejaDeSalida;
	private final Clock reloj;

	public ConfirmacionPorPago(ReservaRepository reservaRepository, BandejaDeEntrada bandejaDeEntrada,
			BandejaDeSalida bandejaDeSalida, Clock reloj) {
		this.reservaRepository = reservaRepository;
		this.bandejaDeEntrada = bandejaDeEntrada;
		this.bandejaDeSalida = bandejaDeSalida;
		this.reloj = reloj;
	}

	@RabbitListener(queues = RabbitConfig.COLA_PAGO_APROBADO)
	@Transactional
	public void alAprobarseElPago(PagoAprobado pago) {
		if (!bandejaDeEntrada.registrar(pago.eventoId(), PagoAprobado.TIPO)) {
			log.info("Evento {} (pago.aprobado) repetido: se ignora", pago.eventoId());
			return;
		}
		Instant ahora = reloj.instant();
		Optional<Reserva> encontrada = reservaRepository.findById(pago.reservaId());
		// Se decide ANTES de llamar a confirmar(): si confirmar() lanzara, la transacción quedaría
		// marcada para rollback y se perdería el reserva.rechazada que guardamos acá mismo.
		Optional<String> impedimento = encontrada
				.map(reserva -> impedimento(reserva, pago, ahora))
				.orElse(Optional.of("La reserva no existe"));
		if (impedimento.isPresent()) {
			log.warn("Pago {} de la reserva {} rechazado: {}", pago.pagoId(), pago.reservaId(), impedimento.get());
			bandejaDeSalida.guardar(ReservaRechazada.de(pago.reservaId(), pago.pagoId(), impedimento.get(), ahora));
			return;
		}
		Reserva reserva = encontrada.get();
		reserva.confirmar(ahora);
		bandejaDeSalida.guardar(ReservaConfirmada.de(reserva.getId(), pago.pagoId(), ahora));
		log.info("Reserva {} confirmada con el pago {}", reserva.getId(), pago.pagoId());
	}

	private static Optional<String> impedimento(Reserva reserva, PagoAprobado pago, Instant ahora) {
		// El monto lo toma pagos-service de la reserva; si no coincide, algo está mal y no se
		// confirma un turno pagado por otro importe.
		if (pago.monto() == null || reserva.getMonto().compareTo(pago.monto()) != 0) {
			return Optional.of("El monto pagado (%s) no coincide con el de la reserva (%s)"
					.formatted(pago.monto(), reserva.getMonto()));
		}
		return reserva.impedimentoParaConfirmar(ahora);
	}

}
