package com.nahumotos.pagos_service.pago;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PagoRepository extends JpaRepository<Pago, Long> {

	/** Por el índice ux_pago_reserva_activo hay a lo sumo uno PENDIENTE y uno APROBADO por reserva. */
	Optional<Pago> findFirstByReservaIdAndEstado(Long reservaId, EstadoPago estado);

	List<Pago> findByEstadoOrderByIdAsc(EstadoPago estado);

}
