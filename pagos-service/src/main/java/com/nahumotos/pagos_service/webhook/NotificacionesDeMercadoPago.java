package com.nahumotos.pagos_service.webhook;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.nahumotos.pagos_service.eventos.BandejaDeSalida;
import com.nahumotos.pagos_service.eventos.PagoAprobado;
import com.nahumotos.pagos_service.mercadopago.MercadoPagoClient;
import com.nahumotos.pagos_service.mercadopago.OrderMp;
import com.nahumotos.pagos_service.pago.EstadoPago;
import com.nahumotos.pagos_service.pago.EstadosDeOrder;
import com.nahumotos.pagos_service.pago.Pago;
import com.nahumotos.pagos_service.pago.PagoRepository;
import com.nahumotos.pagos_service.reembolso.Reembolsos;

/**
 * Procesa una notificación de Mercado Pago ya autenticada. No usa el body de la notificación:
 * consulta la order con {@code GET /v1/orders/{id}} y aplica ese estado, que es el único
 * confiable (un body viejo o reordenado no puede llevar el pago a un estado incorrecto).
 */
@Service
public class NotificacionesDeMercadoPago {

	private static final Logger log = LoggerFactory.getLogger(NotificacionesDeMercadoPago.class);

	/** Nombre del índice único parcial de la migración V1. */
	private static final String INDICE_PAGO_ACTIVO = "ux_pago_reserva_activo";

	private final MercadoPagoClient mercadoPago;
	private final PagoRepository pagoRepository;
	private final BandejaDeSalida bandejaDeSalida;
	private final Reembolsos reembolsos;
	private final Clock reloj;
	private final TransactionTemplate transaccion;

	public NotificacionesDeMercadoPago(MercadoPagoClient mercadoPago, PagoRepository pagoRepository,
			BandejaDeSalida bandejaDeSalida, Reembolsos reembolsos, Clock reloj,
			PlatformTransactionManager transactionManager) {
		this.mercadoPago = mercadoPago;
		this.pagoRepository = pagoRepository;
		this.bandejaDeSalida = bandejaDeSalida;
		this.reembolsos = reembolsos;
		this.reloj = reloj;
		this.transaccion = new TransactionTemplate(transactionManager);
	}

	/**
	 * La consulta a Mercado Pago va fuera de la transacción. Si Mercado Pago no responde, la
	 * excepción termina en un 503 y Mercado Pago reintenta la notificación más tarde.
	 */
	public void procesar(String orderId) {
		Optional<OrderMp> order = mercadoPago.obtenerOrder(orderId);
		if (order.isEmpty()) {
			log.info("Notificación de la order {}, que no existe en Mercado Pago: se ignora", orderId);
			return;
		}
		try {
			transaccion.executeWithoutResult(estado -> aplicar(order.get()));
		}
		catch (DataIntegrityViolationException ex) {
			if (!violaIndiceDePagoActivo(ex)) {
				throw ex;
			}
			// Una order que había quedado RECHAZADA o EXPIRADA terminó cobrada, pero la reserva ya
			// tiene otro pago en curso o aprobado. No puede quedar APROBADO: el cobro sobra y se
			// devuelve. La transacción anterior hizo rollback, así que no quedó pago.aprobado.
			Optional<Long> aReembolsar = transaccion.execute(estado -> pagoDeLaOrder(order.get())
					.map(pago -> {
						pago.registrarCobroSobrante(reloj.instant());
						return pago;
					})
					.filter(pago -> pago.getEstado() == EstadoPago.REEMBOLSO_PENDIENTE)
					.map(Pago::getId));
			if (aReembolsar != null && aReembolsar.isPresent()) {
				log.warn("La order {} se cobró pero la reserva ya tiene otro pago activo: se reembolsa el pago {}",
						order.get().id(), aReembolsar.get());
				reembolsos.intentar(aReembolsar.get());
			}
		}
	}

	private void aplicar(OrderMp order) {
		Optional<Pago> encontrado = pagoDeLaOrder(order);
		if (encontrado.isEmpty()) {
			log.info("La order {} no corresponde a ningún pago de este servicio: se ignora", order.id());
			return;
		}
		Pago pago = encontrado.get();
		Instant ahora = reloj.instant();
		if (!pago.tieneOrder()) {
			// Mercado Pago creó la order pero no llegamos a guardar su id (caída o timeout).
			pago.asociarOrder(order.id(), null, ahora);
		}
		EstadosDeOrder.estadoDePago(order.status()).ifPresent(nuevo -> {
			if (pago.registrarEstadoDeOrder(nuevo, ahora)) {
				// Flush acá: si el pago venía RECHAZADO o EXPIRADO y la reserva ya tiene otro pago
				// activo, ux_pago_reserva_activo salta ahora y procesar() lo resuelve.
				pagoRepository.saveAndFlush(pago);
				// Misma transacción que el cambio de estado: o quedan los dos, o ninguno.
				bandejaDeSalida.guardar(PagoAprobado.de(pago, ahora));
			}
		});
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

	/**
	 * La order es nuestra si su external_reference es el id de uno de nuestros pagos y ese pago
	 * no tiene otra order asociada.
	 */
	private Optional<Pago> pagoDeLaOrder(OrderMp order) {
		Long pagoId;
		try {
			pagoId = Long.valueOf(order.externalReference());
		}
		catch (NumberFormatException ex) {
			return Optional.empty();
		}
		return pagoRepository.findById(pagoId)
				.filter(pago -> !pago.tieneOrder() || pago.getMpOrderId().equals(order.id()));
	}

}
