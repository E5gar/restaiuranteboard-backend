package com.restaiuranteboard.backend.controller;

import com.restaiuranteboard.backend.model.sql.User;
import com.restaiuranteboard.backend.service.TicketSoporteService;
import com.restaiuranteboard.backend.service.chat.ChatService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/soporte")
public class TicketSoporteController {

    private final TicketSoporteService ticketSoporteService;
    private final ChatService chatService;

    public TicketSoporteController(TicketSoporteService ticketSoporteService, ChatService chatService) {
        this.ticketSoporteService = ticketSoporteService;
        this.chatService = chatService;
    }

    @GetMapping("/cliente/pedidos-recientes")
    public ResponseEntity<?> pedidosRecientesCliente() {
        User user = requerirRol("CLIENTE");
        return ResponseEntity.ok(ticketSoporteService.pedidosRecientes48h(user.getId()));
    }

    @PostMapping(value = "/cliente/tickets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> crearTicketCliente(
            @RequestParam("orderId") String orderId,
            @RequestParam("categoria") String categoria,
            @RequestParam("descripcion") String descripcion,
            @RequestParam(value = "evidencia", required = false) MultipartFile evidencia
    ) {
        User user = requerirRol("CLIENTE");
        try {
            return ResponseEntity.ok(ticketSoporteService.crearTicket(
                    user.getId(),
                    orderId,
                    categoria,
                    descripcion,
                    evidencia
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/admin/pendientes-count")
    public ResponseEntity<?> pendientesCount() {
        requerirRol("ADMIN");
        return ResponseEntity.ok(Map.of("pendientes", ticketSoporteService.contarPendientes()));
    }

    @GetMapping("/admin/tickets")
    public ResponseEntity<?> listarAdmin() {
        requerirRol("ADMIN");
        return ResponseEntity.ok(ticketSoporteService.listarTicketsAdmin());
    }

    @GetMapping("/admin/tickets/{ticketId}/evidencia")
    public ResponseEntity<?> evidencia(@PathVariable String ticketId) {
        requerirRol("ADMIN");
        try {
            return ResponseEntity.ok(ticketSoporteService.evidenciaDataUrl(ticketId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/admin/tickets/{ticketId}/cerrar")
    public ResponseEntity<?> cerrar(
            @PathVariable String ticketId,
            @RequestBody Map<String, String> body
    ) {
        User admin = requerirRol("ADMIN");
        try {
            return ResponseEntity.ok(ticketSoporteService.cerrarTicket(
                    ticketId,
                    admin.getEmail(),
                    body.get("mensaje")
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
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
