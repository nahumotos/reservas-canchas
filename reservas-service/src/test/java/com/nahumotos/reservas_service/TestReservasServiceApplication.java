package com.nahumotos.reservas_service;

import org.springframework.boot.SpringApplication;

public class TestReservasServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(ReservasServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
