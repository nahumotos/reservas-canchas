package com.nahumotos.reservas_service;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@OpenAPIDefinition(info = @Info(title = "Reservas Service", version = "v1",
		description = "Reserva de turnos en las canchas de canchas-service"))
public class ReservasServiceApplication {

	public static void main(String[] args) {
		// El driver de PostgreSQL le manda al servidor la zona horaria de la JVM. En Windows con
		// hora argentina esa zona es el alias viejo "America/Buenos_Aires", que postgres:18 ya no
		// trae (Debian lo movió a tzdata-legacy) y rechaza la conexión. Fijamos UTC antes de que
		// arranque Spring para que la JVM se comporte igual en cualquier máquina. La hora local
		// de los clubes sale de la propiedad reservas.zona-horaria, no de la zona de la JVM.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(ReservasServiceApplication.class, args);
	}

}
