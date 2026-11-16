package com.nahumotos.pagos_service;

import org.springframework.boot.SpringApplication;

public class TestPagosServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(PagosServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
