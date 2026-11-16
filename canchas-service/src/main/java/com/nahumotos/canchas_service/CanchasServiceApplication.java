package com.nahumotos.canchas_service;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(title = "Canchas Service", version = "v1",
		description = "Gestión de clubes y sus canchas"))
public class CanchasServiceApplication {

	public static void main(String[] args) {
		// El driver de PostgreSQL le manda al servidor la zona horaria de la JVM. En Windows con
		// hora argentina esa zona es el alias viejo "America/Buenos_Aires", que postgres:18 ya no
		// trae (Debian lo movió a tzdata-legacy) y rechaza la conexión. Fijamos UTC antes de que
		// arranque Spring para que la JVM se comporte igual en cualquier máquina.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(CanchasServiceApplication.class, args);
	}

}
