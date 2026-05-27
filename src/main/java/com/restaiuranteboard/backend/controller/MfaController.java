package com.restaiuranteboard.backend.controller;

import com.restaiuranteboard.backend.model.sql.User;
import com.restaiuranteboard.backend.repository.sql.UserRepository;
import com.restaiuranteboard.backend.service.GoogleAuthService;
import com.restaiuranteboard.backend.service.GoogleTokenVerifierService;
import com.restaiuranteboard.backend.service.MfaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/perfil/me/mfa")
public class MfaController {

    private final UserRepository userRepository;
    private final MfaService mfaService;
    private final GoogleTokenVerifierService googleTokenVerifierService;

    public MfaController(UserRepository userRepository, MfaService mfaService, GoogleTokenVerifierService googleTokenVerifierService) {
        this.userRepository = userRepository;
        this.mfaService = mfaService;
        this.googleTokenVerifierService = googleTokenVerifierService;
    }

    @GetMapping("/estado")
    public ResponseEntity<?> estado() {
        User user = obtenerUsuarioAutenticado();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "No autorizado."));
        }
        return ResponseEntity.ok(Map.of(
                "mfaEnabled", user.isMfaEnabled(),
                "pendingSetup", !user.isMfaEnabled() && user.getMfaSecret() != null && !user.getMfaSecret().isBlank()
        ));
    }

    @PostMapping("/iniciar")
    public ResponseEntity<?> iniciar() {
        User user = obtenerUsuarioAutenticado();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "No autorizado."));
        }
        try {
            MfaService.MfaSetupStartResult result = mfaService.iniciarConfiguracion(user);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("otpAuthUri", result.otpAuthUri());
            body.put("secretPlain", result.secretPlain());
            body.put("email", user.getEmail());
            return ResponseEntity.ok(body);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/confirmar")
    public ResponseEntity<?> confirmar(@RequestBody Map<String, String> body) {
        User user = obtenerUsuarioAutenticado();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "No autorizado."));
        }
        String code = body.get("code");
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "El código es obligatorio."));
        }
        try {
            user = userRepository.findByEmail(user.getEmail()).orElse(user);
            List<String> backupCodes = mfaService.confirmarActivacion(user, code);
            return ResponseEntity.ok(Map.of(
                    "message", "Autenticación de doble factor activada.",
                    "mfaEnabled", true,
                    "backupCodes", backupCodes
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/desactivar")
    public ResponseEntity<?> desactivar(@RequestBody Map<String, String> body) {
        User user = obtenerUsuarioAutenticado();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "No autorizado."));
        }
        user = userRepository.findByEmail(user.getEmail()).orElse(user);
        boolean tienePassword = user.getPassword() != null && !user.getPassword().isBlank();

        String password = body.get("password");
        String code = body.get("code");
        String backupCode = body.get("backupCode");
        String idToken = body.get("idToken");
        String googleCode = body.get("googleCode");

        if (tienePassword || !GoogleAuthService.esSoloGoogle(user)) {
            if (password == null || password.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "La contraseña es obligatoria."));
            }
            if (code == null || code.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "El código del autenticador es obligatorio."));
            }
            try {
                mfaService.desactivar(user, password, code);
                return ResponseEntity.ok(Map.of(
                        "message", "Autenticación de doble factor desactivada.",
                        "mfaEnabled", false
                ));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
            } catch (IllegalStateException e) {
                return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
            }
        }

        if (!googleTokenVerifierService.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "message",
                    "Verificación con Google no está configurada."
            ));
        }
        var profileOpt = googleTokenVerifierService.resolveProfile(idToken, googleCode);
        if (profileOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "No se pudo verificar la cuenta de Google."));
        }
        String email = profileOpt.get().email();
        if (email == null || !email.equalsIgnoreCase(user.getEmail())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "La cuenta de Google no coincide con la sesión activa."));
        }

        if ((code == null || code.isBlank()) && (backupCode == null || backupCode.isBlank())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Ingresa el código de 6 dígitos o un código de respaldo."));
        }
        try {
            mfaService.desactivarSinPassword(user, code, backupCode);
            return ResponseEntity.ok(Map.of(
                    "message", "Autenticación de doble factor desactivada.",
                    "mfaEnabled", false
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    private User obtenerUsuarioAutenticado() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() == null
                ? null
                : SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof String email) || email.isBlank()) {
            return null;
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || user.isDeleted()) {
            return null;
        }
        return user;
    }
}
