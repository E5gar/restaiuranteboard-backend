package com.restaiuranteboard.backend.controller;

import com.restaiuranteboard.backend.model.sql.User;
import com.restaiuranteboard.backend.service.chat.ChatService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/cliente/sesion")
    public ResponseEntity<?> sesionCliente() {
        User user = requerirRol("CLIENTE");
        return ResponseEntity.ok(chatService.obtenerSesionActiva(user, "CLIENTE"));
    }

    @PostMapping("/cliente/nueva-sesion")
    public ResponseEntity<?> nuevaSesionCliente() {
        User user = requerirRol("CLIENTE");
        return ResponseEntity.ok(chatService.nuevaSesion(user, "CLIENTE"));
    }

    @GetMapping("/cliente/sesiones")
    public ResponseEntity<List<Map<String, Object>>> listarSesionesCliente() {
        User user = requerirRol("CLIENTE");
        return ResponseEntity.ok(chatService.listarSesiones(user, "CLIENTE"));
    }

    @GetMapping("/cliente/sesiones/{sessionId}")
    public ResponseEntity<?> obtenerSesionCliente(@PathVariable String sessionId) {
        User user = requerirRol("CLIENTE");
        try {
            return ResponseEntity.ok(chatService.obtenerSesionPorId(user, "CLIENTE", sessionId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/cliente/mensaje")
    public ResponseEntity<?> mensajeCliente(@RequestBody Map<String, String> body) {
        User user = requerirRol("CLIENTE");
        try {
            return ResponseEntity.ok(chatService.enviarMensaje(
                    user,
                    "CLIENTE",
                    body.get("sessionId"),
                    body.get("message")
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("reply", "No puedo realizar ello.", "closed", false));
        }
    }

    @GetMapping("/admin/sesion")
    public ResponseEntity<?> sesionAdmin() {
        User user = requerirRol("ADMIN");
        return ResponseEntity.ok(chatService.obtenerSesionActiva(user, "ADMIN"));
    }

    @PostMapping("/admin/nueva-sesion")
    public ResponseEntity<?> nuevaSesionAdmin() {
        User user = requerirRol("ADMIN");
        return ResponseEntity.ok(chatService.nuevaSesion(user, "ADMIN"));
    }

    @GetMapping("/admin/sesiones")
    public ResponseEntity<List<Map<String, Object>>> listarSesionesAdmin() {
        User user = requerirRol("ADMIN");
        return ResponseEntity.ok(chatService.listarSesiones(user, "ADMIN"));
    }

    @GetMapping("/admin/sesiones/{sessionId}")
    public ResponseEntity<?> obtenerSesionAdmin(@PathVariable String sessionId) {
        User user = requerirRol("ADMIN");
        try {
            return ResponseEntity.ok(chatService.obtenerSesionPorId(user, "ADMIN", sessionId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/admin/mensaje")
    public ResponseEntity<?> mensajeAdmin(@RequestBody Map<String, String> body) {
        User user = requerirRol("ADMIN");
        try {
            return ResponseEntity.ok(chatService.enviarMensaje(
                    user,
                    "ADMIN",
                    body.get("sessionId"),
                    body.get("message")
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("reply", "No puedo realizar ello.", "closed", false));
        }
    }

    private User requerirRol(String rolEsperado) {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof String email) || email.isBlank()) {
            throw new IllegalArgumentException("No autenticado.");
        }
        User user = chatService.requerirUsuarioPorEmail(email);
        String rol = user.getRole() == null ? "" : user.getRole().getName();
        if (!rolEsperado.equals(rol)) {
            throw new IllegalArgumentException("Rol no autorizado.");
        }
        return user;
    }
}
