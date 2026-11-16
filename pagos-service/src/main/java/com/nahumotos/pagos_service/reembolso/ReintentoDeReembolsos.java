package com.nahumotos.pagos_service.reembolso;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.nahumotos.pagos_service.pago.EstadoPago;
import com.nahumotos.pagos_service.pago.Pago;
import com.nahumotos.pagos_service.pago.PagoRepository;

/**
 * Reintenta los reembolsos que quedaron pendientes porque Mercado Pago falló (o porque el
 * servicio se cayó entre el commit y la llamada). Es seguro con varias instancias: todas usan la
 * misma X-Idempotency-Key por pago.
 */
@Component
public class ReintentoDeReembolsos {

	private final PagoRepository pagoRepository;
	private final Reembolsos reembolsos;

	public ReintentoDeReembolsos(PagoRepository pagoRepository, Reembolsos reembolsos) {
		this.pagoRepository = pagoRepository;
		this.reembolsos = reembolsos;
	}

	/** Corre según {@code pagos.reembolsos.cron} (cada minuto); devuelve cuántos intentó. */
	@Scheduled(cron = "${pagos.reembolsos.cron}")
	public int reintentar() {
		List<Long> pendientes = pagoRepository.findByEstadoOrderByIdAsc(EstadoPago.REEMBOLSO_PENDIENTE).stream()
				.map(Pago::getId)
				.toList();
		pendientes.forEach(reembolsos::intentar);
		return pendientes.size();
	}

}
