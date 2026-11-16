package com.nahumotos.canchas_service.club;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nahumotos.canchas_service.error.RecursoNoEncontradoException;

@Service
@Transactional(readOnly = true)
public class ClubService {

	private final ClubRepository clubRepository;

	public ClubService(ClubRepository clubRepository) {
		this.clubRepository = clubRepository;
	}

	@Transactional
	public ClubResponse crear(CrearClubRequest request) {
		var club = new Club(request.nombre(), request.direccion(), request.horaApertura(), request.horaCierre());
		return ClubResponse.desde(clubRepository.save(club));
	}

	public List<ClubResponse> listar() {
		return clubRepository.findAll(Sort.by("nombre")).stream()
				.map(ClubResponse::desde)
				.toList();
	}

	public ClubResponse obtener(Long id) {
		return ClubResponse.desde(buscarClub(id));
	}

	/**
	 * Devuelve la entidad o lanza 404. Es la puerta de entrada del módulo cancha
	 * al módulo club: así cancha no accede directamente a ClubRepository.
	 */
	public Club buscarClub(Long id) {
		return clubRepository.findById(id)
				.orElseThrow(() -> new RecursoNoEncontradoException("No existe el club con id " + id));
	}

	public void verificarQueExiste(Long id) {
		if (!clubRepository.existsById(id)) {
			throw new RecursoNoEncontradoException("No existe el club con id " + id);
		}
	}

}
