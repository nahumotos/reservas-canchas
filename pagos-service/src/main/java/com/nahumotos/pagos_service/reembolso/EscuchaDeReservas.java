package com.nahumotos.pagos_service.reembolso;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.nahumotos.pagos_service.eventos.BandejaDeEntrada;
import com.nahumotos.pagos_service.eventos.RabbitConfig;
import com.nahumotos.pagos_service.eventos.ReservaCancelada;
import com.nahumotos.pagos_service.eventos.ReservaRechazada;
import com.nahumotos.pagos_service.pago.EstadoPago;
import com.nahumotos.pagos_service.pago.Pago;
import com.nahumotos.pagos_service.pago.PagoRepository;

/**
 * Eventos de reservas-service que obligan a devolver la plata. En una transacción se registra el
 * evento en el inbox y el pago pasa a REEMBOLSO_PENDIENTE; después del commit, fuera de la
 * transacción, se le pide el reembolso a Mercado Pago. Si eso falla, el mensaje igual se da por
 * procesado: el reembolso queda pendiente y lo completa {@link ReintentoDeReembolsos}.
 */
@Component
public class EscuchaDeReservas {

	private static final Logger log = LoggerFactory.getLogger(EscuchaDeReservas.class);

	private final BandejaDeEntrada bandejaDeEntrada;
	private final PagoRepository pagoRepository;
	private final Reembolsos reembolsos;
	private final Clock reloj;
	private final TransactionTemplate transaccion;

	public EscuchaDeReservas(BandejaDeEntrada bandejaDeEntrada, PagoRepository pagoRepository, Reembolsos reembolsos,
			Clock reloj, PlatformTransactionManager transactionManager) {
		this.bandejaDeEntrada = bandejaDeEntrada;
		this.pagoRepository = pagoRepository;
		this.reembolsos = reembolsos;
		this.reloj = reloj;
		this.transaccion = new TransactionTemplate(transactionManager);
	}

	/** La reserva del pago no se pudo confirmar: se reembolsa ese pago. */
	@RabbitListener(queues = RabbitConfig.COLA_RESERVA_RECHAZADA)
	public void alRechazarseLaReserva(ReservaRechazada evento) {
		log.info("Reserva {} rechazada para el pago {}: {}", evento.reservaId(), evento.pagoId(), evento.motivo());
		reembolsar(evento.eventoId(), ReservaRechazada.TIPO, () -> pagoRepository.findById(evento.pagoId()));
	}

	/** El cliente canceló una reserva confirmada: se reembolsa el pago aprobado de esa reserva. */
	@RabbitListener(queues = RabbitConfig.COLA_RESERVA_CANCELADA)
	public void alCancelarseLaReserva(ReservaCancelada evento) {
		log.info("Reserva {} cancelada", evento.reservaId());
		reembolsar(evento.eventoId(), ReservaCancelada.TIPO,
				() -> pagoRepository.findFirstByReservaIdAndEstado(evento.reservaId(), EstadoPago.APROBADO));
	}

	private void reembolsar(UUID eventoId, String tipo, Supplier<Optional<Pago>> pagoAReembolsar) {
		Optional<Long> pendiente = transaccion.execute(estado -> {
			if (!bandejaDeEntrada.registrar(eventoId, tipo)) {
				log.info("Evento {} ({}) repetido: se ignora", eventoId, tipo);
				return Optional.<Long>empty();
			}
			Optional<Pago> pago = pagoAReembolsar.get();
			if (pago.isEmpty()) {
				log.warn("Evento {} ({}): no hay un pago aprobado para reembolsar", eventoId, tipo);
			}
			return pago.filter(encontrado -> encontrado.solicitarReembolso(reloj.instant())).map(Pago::getId);
		});
		if (pendiente != null) {
			pendiente.ifPresent(reembolsos::intentar);
		}
	}

}
