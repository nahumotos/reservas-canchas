package com.nahumotos.canchas_service.cancha;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CanchaRepository extends JpaRepository<Cancha, Long> {

	List<Cancha> findByClubIdOrderByNombreAsc(Long clubId);

	boolean existsByClubIdAndNombre(Long clubId, String nombre);

}
