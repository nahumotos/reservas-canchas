package com.nahumotos.canchas_service;

import java.util.TimeZone;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CanchasServiceApplicationTests {

	static {
		// Los tests no pasan por el main de la aplicación, así que repetimos acá lo que hace
		// CanchasServiceApplication.main: JVM en UTC para que postgres:18 acepte la conexión.
		// El bloque corre al cargar la clase, antes de que Spring abra cualquier conexión.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}

	@Test
	void contextLoads() {
	}

}
