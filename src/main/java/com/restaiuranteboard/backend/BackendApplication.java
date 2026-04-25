package com.restaiuranteboard.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import jakarta.annotation.PostConstruct;
import java.util.TimeZone;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

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
