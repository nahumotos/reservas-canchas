package com.nahumotos.pagos_service.reembolso;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import com.nahumotos.pagos_service.error.ServicioNoDisponibleException;
import com.nahumotos.pagos_service.mercadopago.MercadoPagoClient;
import com.nahumotos.pagos_service.pago.EstadoPago;
import com.nahumotos.pagos_service.pago.Pago;
import com.nahumotos.pagos_service.pago.PagoRepository;

/**
 * Pide a Mercado Pago el reembolso total de un pago en REEMBOLSO_PENDIENTE. Lo usan los
 * listeners (en cuanto llega el evento) y {@link ReintentoDeReembolsos} (para los que fallaron).
 * Siempre con la misma X-Idempotency-Key por pago, así dos intentos (aunque se pisen) no
 * devuelven la plata dos veces.
 */
@Service
public class Reembolsos {

	private static final Logger log = LoggerFactory.getLogger(Reembolsos.class);

	private final PagoRepository pagoRepository;
	private final MercadoPagoClient mercadoPago;
	private final Clock reloj;
	private final TransactionTemplate transaccion;

	public Reembolsos(PagoRepository pagoRepository, MercadoPagoClient mercadoPago, Clock reloj,
			PlatformTransactionManager transactionManager) {
		this.pagoRepository = pagoRepository;
		this.mercadoPago = mercadoPago;
		this.reloj = reloj;
		this.transaccion = new TransactionTemplate(transactionManager);
	}

	/**
	 * Fuera de transacción: la llamada a Mercado Pago no retiene una conexión a la base. Nunca
	 * lanza por una falla de Mercado Pago. Si la falla es transitoria (caída, timeout, 5xx, 429)
	 * el pago queda REEMBOLSO_PENDIENTE y se reintenta; si es un 4xx permanente (no 409 ni 429:
	 * esos los resuelve el cliente) queda REEMBOLSO_FALLIDO y no se reintenta solo.
	 */
	public void intentar(Long pagoId) {
		Pago pago = pagoRepository.findById(pagoId).orElse(null);
		if (pago == null || pago.getEstado() != EstadoPago.REEMBOLSO_PENDIENTE) {
			return;
		}
		boolean reembolsado;
		try {
			reembolsado = mercadoPago.reembolsar(pago.getMpOrderId(), pago.claveDeReembolso())
					// 409: ya reembolsada, reembolso en curso o clave ya usada (un intento anterior
					// que sí llegó). Sólo lo damos por hecho si la order ya figura reembolsada.
					|| mercadoPago.obtenerOrder(pago.getMpOrderId())
							.map(order -> "refunded".equals(order.status()))
							.orElse(false);
		}
		catch (HttpClientErrorException ex) {
			// Token inválido, monto que no cuadra, order inexistente…: reintentar cada minuto no lo
			// arregla y sólo llenaría el log. Queda para revisar a mano (el body no se logea: puede
			// traer datos del pagador).
			log.error("Mercado Pago rechazó el reembolso del pago {} (order {}) con {}: queda REEMBOLSO_FALLIDO y "
					+ "hay que revisarlo a mano", pagoId, pago.getMpOrderId(), ex.getStatusCode().value());
			transaccion.executeWithoutResult(estado -> pagoRepository.findById(pagoId)
					.ifPresent(actual -> actual.marcarReembolsoFallido(reloj.instant())));
			return;
		}
		catch (ServicioNoDisponibleException | RestClientException ex) {
			log.warn("No se pudo reembolsar el pago {} (order {}); queda REEMBOLSO_PENDIENTE y se reintenta: {}",
					pagoId, pago.getMpOrderId(), ex.getMessage());
			return;
		}
		if (!reembolsado) {
			log.warn("Mercado Pago todavía no confirma el reembolso del pago {} (order {}); se reintenta", pagoId,
					pago.getMpOrderId());
			return;
		}
		try {
			transaccion.executeWithoutResult(estado -> pagoRepository.findById(pagoId)
					.ifPresent(actual -> actual.marcarReembolsado(reloj.instant())));
			log.info("Pago {} reembolsado (order {})", pagoId, pago.getMpOrderId());
		}
		catch (ObjectOptimisticLockingFailureException ex) {
			// Otro intento (o la notificación "refunded" de Mercado Pago) lo marcó al mismo tiempo.
			log.info("El pago {} cambió mientras se marcaba como reembolsado; lo resuelve el otro proceso", pagoId);
		}
	}

}
