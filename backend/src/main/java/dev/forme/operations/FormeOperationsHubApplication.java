package dev.forme.operations;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FormeOperationsHubApplication {

	public static void main(String[] args) {
		SpringApplication.run(FormeOperationsHubApplication.class, args);
	}

}
