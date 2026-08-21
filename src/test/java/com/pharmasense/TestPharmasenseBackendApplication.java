package com.pharmasense;

import org.springframework.boot.SpringApplication;

public class TestPharmasenseBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(PharmasenseBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
