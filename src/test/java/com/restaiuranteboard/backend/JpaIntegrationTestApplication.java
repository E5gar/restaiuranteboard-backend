package com.restaiuranteboard.backend;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackages = "com.restaiuranteboard.backend.model.sql")
@EnableJpaRepositories(basePackages = "com.restaiuranteboard.backend.repository.sql")
public class JpaIntegrationTestApplication {
}
