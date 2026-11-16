package com.nahumotos.canchas_service.cancha;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nahumotos.canchas_service.club.Club;
import com.nahumotos.canchas_service.club.ClubService;
import com.nahumotos.canchas_service.error.RecursoDuplicadoException;
import com.nahumotos.canchas_service.error.RecursoNoEncontradoException;

@Service
@Transactional(readOnly = true)
public class CanchaService {

	private final CanchaRepository canchaRepository;
	private final ClubService clubService;

	public CanchaService(CanchaRepository canchaRepository, ClubService clubService) {
		this.canchaRepository = canchaRepository;
		this.clubService = clubService;
	}

	@Transactional
	public CanchaResponse crear(Long clubId, CrearCanchaRequest request) {
		Club club = clubService.buscarClub(clubId);
		if (canchaRepository.existsByClubIdAndNombre(clubId, request.nombre())) {
			throw nombreDuplicado(clubId, request.nombre());
		}
		var cancha = new Cancha(club, request.nombre(), request.deporte(), request.precioHora());
		try {
			return CanchaResponse.desde(canchaRepository.saveAndFlush(cancha));
		}
		catch (DataIntegrityViolationException ex) {
			// Dos altas simultáneas con el mismo nombre pueden pasar el chequeo de arriba;
			// el UNIQUE (club_id, nombre) de la tabla frena la segunda y la informamos como 409.
			throw nombreDuplicado(clubId, request.nombre());
		}
	}

	public List<CanchaResponse> listarPorClub(Long clubId) {
		clubService.verificarQueExiste(clubId);
		return canchaRepository.findByClubIdOrderByNombreAsc(clubId).stream()
				.map(CanchaResponse::desde)
				.toList();
	}

	public CanchaResponse obtener(Long id) {
		return canchaRepository.findById(id)
				.map(CanchaResponse::desde)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe la cancha con id " + id));
	}

	private static RecursoDuplicadoException nombreDuplicado(Long clubId, String nombre) {
		return new RecursoDuplicadoException("Ya existe una cancha llamada '" + nombre + "' en el club " + clubId);
	}

}
