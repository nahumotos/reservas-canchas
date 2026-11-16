package com.nahumotos.reservas_service;

import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.nahumotos.reservas_service.canchas.CanchaRemota;
import com.nahumotos.reservas_service.canchas.CanchasClient;
import com.nahumotos.reservas_service.canchas.ClubRemoto;
import com.nahumotos.reservas_service.reserva.ReservaRepository;

/**
 * Base de los tests de integración: PostgreSQL real (Testcontainers), canchas-service
 * simulado y un reloj fijo. Todas las subclases comparten esta misma configuración, así
 * Spring reutiliza un único contexto y un único contenedor para toda la suite.
 * <p>
 * Escenario por defecto: hoy es el 10/06/2030 a las 12:00 (hora argentina); la cancha 10
 * está activa y pertenece al club 1, que abre de 08:00 a 23:00.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "reservas.expiracion.cron=-")
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, RelojDeTestConfiguration.class })
public abstract class PruebaDeIntegracion {

	public static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");
	public static final Long CANCHA_ID = 10L;
	public static final Long CLUB_ID = 1L;
	public static final LocalDate HOY = LocalDate.of(2030, 6, 10);
	public static final LocalDate MANIANA = HOY.plusDays(1);
	public static final LocalDateTime AHORA = LocalDateTime.of(HOY, LocalTime.NOON);

	@Autowired
	protected MockMvc mockMvc;

	@Autowired
	protected ReservaRepository reservaRepository;

	@Autowired
	protected JdbcTemplate jdbc;

	@Autowired
	protected RelojDeTest reloj;

	@MockitoBean
	protected CanchasClient canchasClient;

	@BeforeEach
	void prepararEscenarioBase() {
		reservaRepository.deleteAll();
		reloj.fijar(AHORA);
		when(canchasClient.obtenerCancha(CANCHA_ID)).thenReturn(cancha(true));
		when(canchasClient.obtenerClub(CLUB_ID)).thenReturn(club(LocalTime.of(8, 0), LocalTime.of(23, 0)));
	}

	protected static CanchaRemota cancha(boolean activa) {
		return new CanchaRemota(CANCHA_ID, CLUB_ID, "Cancha 1", "FUTBOL5", new BigDecimal("25000.00"), activa);
	}

	protected static ClubRemoto club(LocalTime apertura, LocalTime cierre) {
		return new ClubRemoto(CLUB_ID, "Club Atlético Palermo", apertura, cierre);
	}

}
