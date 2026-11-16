package com.nahumotos.reservas_service.reserva;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Reserva. Fecha y horas en hora local del club; venceEn y creadaEn en UTC.")
public record ReservaResponse(
		@Schema(example = "1") Long id,
		@Schema(example = "1") Long canchaId,
		@Schema(example = "1") Long clubId,
		@Schema(example = "2026-12-01") LocalDate fecha,
		@Schema(example = "18:00:00") LocalTime horaInicio,
		@Schema(example = "19:00:00") LocalTime horaFin,
		@Schema(example = "Ana Pérez") String clienteNombre,
		@Schema(example = "ana@mail.com") String clienteEmail,
		@Schema(description = "Precio de la cancha al momento de reservar", example = "25000.00") BigDecimal monto,
		@Schema(example = "PENDIENTE_PAGO") EstadoReserva estado,
		@Schema(description = "Hasta cuándo se puede confirmar (pagar): lo que pase primero entre el plazo de pago "
				+ "y el inicio del turno", example = "2026-11-30T15:10:00Z") Instant venceEn,
		@Schema(example = "2026-11-30T15:00:00Z") Instant creadaEn) {

	static ReservaResponse desde(Reserva reserva) {
		return new ReservaResponse(reserva.getId(), reserva.getCanchaId(), reserva.getClubId(), reserva.getFecha(),
				reserva.getHoraInicio(), reserva.getHoraFin(), reserva.getClienteNombre(), reserva.getClienteEmail(),
				reserva.getMonto(), reserva.getEstado(), reserva.getVenceEn(), reserva.getCreadaEn());
	}

}
