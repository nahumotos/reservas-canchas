package com.nahumotos.pagos_service.eventos;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface EventoSalienteRepository extends JpaRepository<EventoSaliente, UUID> {

	/**
	 * Los eventos sin enviar más viejos, bloqueados hasta el fin de la transacción.
	 * SKIP LOCKED: si corren dos instancias del servicio, cada una toma eventos distintos en vez
	 * de publicar los mismos dos veces o quedarse esperando a la otra.
	 */
	@Query(value = """
			select * from evento_saliente
			where enviado_en is null
			order by ocurrido_en, id
			limit :limite
			for update skip locked
			""", nativeQuery = true)
	List<EventoSaliente> bloquearPendientes(int limite);

}
