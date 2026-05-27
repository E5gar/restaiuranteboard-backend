package com.restaiuranteboard.backend.controller;

import com.restaiuranteboard.backend.service.PersonalAdminService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/personal")
public class AdminPersonalController {

    private final PersonalAdminService personalAdminService;

    public AdminPersonalController(PersonalAdminService personalAdminService) {
        this.personalAdminService = personalAdminService;
    }

    @GetMapping("/activos")
    public ResponseEntity<?> listarActivos() {
        List<PersonalAdminService.PersonalActivoDto> out = personalAdminService.listarPersonalActivo();
        return ResponseEntity.ok(out);
    }

    @PostMapping("/{userId}/eliminar")
    public ResponseEntity<?> eliminar(@PathVariable("userId") String userId) {
        UUID id;
        try {
            id = UUID.fromString(userId);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", "Empleado inválido."));
        }

        try {
            personalAdminService.eliminarEmpleado(id);
            return ResponseEntity.ok(Map.of("message", "Empleado dado de baja correctamente."));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "No se pudo eliminar el empleado."));
        }
    }
}

