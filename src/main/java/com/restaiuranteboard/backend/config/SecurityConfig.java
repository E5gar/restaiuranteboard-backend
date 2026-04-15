package com.restaiuranteboard.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.Customizer;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // Deshabilitamos CSRF para poder usar Postman/Angular sin tokens por ahora
            .cors(Customizer.withDefaults()) // Permite las configuraciones de @CrossOrigin de tus controladores
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll() // PERMITIMOS TODO por ahora para que no te bloquee el desarrollo
            );
        
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // Este bean permitirá a AuthController encriptar las claves
    }
}