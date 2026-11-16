package com.nahumotos.reservas_service.canchas;

import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** El club tal como lo devuelve canchas-service (GET /api/clubes/{id}); sólo lo que usamos. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClubRemoto(Long id, String nombre, LocalTime horaApertura, LocalTime horaCierre) {
}
