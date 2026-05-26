package com.restaiuranteboard.backend.controller;

import com.restaiuranteboard.backend.service.GoogleAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/google")
public class GoogleAuthController {

    @Autowired
    private GoogleAuthService googleAuthService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        return googleAuthService.login(body);
    }

    @PostMapping("/sesion-registro")
    public ResponseEntity<?> sesionRegistro(@RequestBody Map<String, String> body) {
        return googleAuthService.iniciarSesionRegistro(body);
    }

    @PostMapping("/registrar")
    public ResponseEntity<?> registrar(@RequestBody Map<String, String> body) {
        return googleAuthService.registrar(body);
    }
}
