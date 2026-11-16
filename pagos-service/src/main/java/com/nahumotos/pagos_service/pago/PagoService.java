package com.nahumotos.pagos_service.pago;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.nahumotos.pagos_service.config.PagosProperties;
import com.nahumotos.pagos_service.error.ConflictoException;
import com.nahumotos.pagos_service.error.RecursoNoEncontradoException;
import com.nahumotos.pagos_service.mercadopago.MercadoPagoClient;
import com.nahumotos.pagos_service.mercadopago.NuevaOrder;
import com.nahumotos.pagos_service.mercadopago.OrderMp;
import com.nahumotos.pagos_service.reservas.ReservaRemota;
import com.nahumotos.pagos_service.reservas.ReservasClient;

@Service
public class PagoService {

	/** Nombre del índice único parcial de la migración V1. */
	private static final String INDICE_PAGO_ACTIVO = "ux_pago_reserva_activo";

	private final PagoRepository pagoRepository;
	private final ReservasClient reservasClient;
	private final MercadoPagoClient mercadoPago;
	private final Clock reloj;
	private final TransactionTemplate transaccion;
	private final Duration margenMinimoParaPagar;

	public PagoService(PagoRepository pagoRepository, ReservasClient reservasClient, MercadoPagoClient mercadoPago,
			Clock reloj, PlatformTransactionManager transactionManager, PagosProperties propiedades) {
		this.pagoRepository = pagoRepository;
		this.reservasClient = reservasClient;
		this.mercadoPago = mercadoPago;
		this.reloj = reloj;
		this.transaccion = new TransactionTemplate(transactionManager);
		this.margenMinimoParaPagar = propiedades.margenMinimoParaPagar();
	}

	/** Resultado de {@link #crear}: el pago y si se creó en este pedido (201) o ya existía (200). */
	public record PagoCreado(PagoResponse pago, boolean nuevo) {
	}

	/**
	 * Sin @Transactional a propósito, con el mismo criterio que ReservaService.crear: las
	 * llamadas a reservas-service y a Mercado Pago no retienen una conexión a la base. Primero se
	 * guarda el pago (la order necesita su id como external_reference) y después se crea la order
	 * con la clave de idempotencia del pago. Si Mercado Pago falla, el pago queda PENDIENTE sin
	 * order y el próximo pedido la vuelve a pedir con la misma clave.
	 */
	public PagoCreado crear(Long reservaId) {
		ReservaRemota reserva = reservasClient.obtenerReserva(reservaId);
		Instant ahora = reloj.instant();
		validarQueSePuedePagar(reserva, ahora);

		PagoCreado registrado = registrarPago(reserva, ahora);
		Long pagoId = registrado.pago().pagoId();
		if (registrado.pago().checkoutUrl() != null) {
			return registrado;
		}
		Pago pago = transaccion.execute(estado -> buscar(pagoId));
		// La order vence cuando vence la reserva, redondeado hacia abajo al minuto: nunca se puede
		// pagar una reserva que ya expiró.
		Duration vigencia = Duration.between(ahora, reserva.venceEn()).truncatedTo(ChronoUnit.MINUTES);
		OrderMp order = mercadoPago.crearOrder(new NuevaOrder(pagoId.toString(), pago.getMonto(), vigencia,
				"Reserva de cancha #" + reservaId), pago.getClaveIdempotencia());

		PagoResponse conOrder = transaccion.execute(estado -> {
			Pago actual = buscar(pagoId);
			actual.asociarOrder(order.id(), order.checkoutUrl(), reloj.instant());
			return PagoResponse.desde(actual);
		});
		return new PagoCreado(conOrder, registrado.nuevo());
	}

	@Transactional(readOnly = true)
	public PagoResponse obtener(Long id) {
		return PagoResponse.desde(buscar(id));
	}

	private void validarQueSePuedePagar(ReservaRemota reserva, Instant ahora) {
		if (!reserva.pendienteDePago()) {
			throw new ConflictoException("La reserva %d no está pendiente de pago (estado: %s)"
					.formatted(reserva.id(), reserva.estado()));
		}
		if (Duration.between(ahora, reserva.venceEn()).compareTo(margenMinimoParaPagar) < 0) {
			throw new ConflictoException(
					"La reserva %d está por vencer y ya no hay tiempo para pagarla".formatted(reserva.id()));
		}
	}

	/** Devuelve el pago PENDIENTE de la reserva o, si no hay, crea uno nuevo. */
	private PagoCreado registrarPago(ReservaRemota reserva, Instant ahora) {
		try {
			return transaccion.execute(estado -> pagoRepository
					.findFirstByReservaIdAndEstado(reserva.id(), EstadoPago.PENDIENTE)
					.map(existente -> new PagoCreado(PagoResponse.desde(existente), false))
					.orElseGet(() -> new PagoCreado(PagoResponse.desde(pagoRepository.saveAndFlush(
							new Pago(reserva.id(), reserva.monto(), UUID.randomUUID(), ahora))), true)));
		}
		catch (DataIntegrityViolationException ex) {
			if (!violaIndiceDePagoActivo(ex)) {
				throw ex;
			}
			// Otro pedido simultáneo creó el pago, o la reserva ya tiene uno APROBADO (el evento
			// todavía no llegó a reservas-service). El índice único es la fuente de verdad.
			return transaccion.execute(estado -> pagoRepository
					.findFirstByReservaIdAndEstado(reserva.id(), EstadoPago.PENDIENTE)
					.map(existente -> new PagoCreado(PagoResponse.desde(existente), false))
					.orElseThrow(() -> new ConflictoException(
							"La reserva " + reserva.id() + " ya tiene un pago aprobado")));
		}
	}

	private Pago buscar(Long id) {
		return pagoRepository.findById(id)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el pago con id " + id));
	}

	private static boolean violaIndiceDePagoActivo(DataIntegrityViolationException ex) {
		for (Throwable causa = ex; causa != null; causa = causa.getCause()) {
			if (causa instanceof ConstraintViolationException violacion
					&& INDICE_PAGO_ACTIVO.equalsIgnoreCase(violacion.getConstraintName())) {
				return true;
			}
		}
		return false;
	}

}
