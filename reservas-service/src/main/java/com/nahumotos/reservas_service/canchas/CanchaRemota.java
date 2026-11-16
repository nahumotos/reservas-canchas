package com.nahumotos.reservas_service.canchas;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * La cancha tal como la devuelve canchas-service (GET /api/canchas/{id}). Ignora campos
 * desconocidos para no romperse si canchas-service agrega alguno, y el deporte va como
 * String para no acoplarse a su enum.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CanchaRemota(Long id, Long clubId, String nombre, String deporte, BigDecimal precioHora, boolean activa) {
}
