package com.restaiuranteboard.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import jakarta.annotation.PostConstruct;
import java.util.TimeZone;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.restaiuranteboard.backend.repository.sql")
@EnableMongoRepositories(basePackages = "com.restaiuranteboard.backend.repository.nosql")

public class BackendApplication {

	@PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Lima"));
    }

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}
