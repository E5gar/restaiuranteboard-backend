package com.restaiuranteboard.backend.controller;

import com.restaiuranteboard.backend.model.nosql.ConfiguracionSistema;
import com.restaiuranteboard.backend.model.sql.VerificationCode;
import com.restaiuranteboard.backend.repository.nosql.ConfiguracionSistemaRepository;
import com.restaiuranteboard.backend.repository.sql.VerificationCodeRepository;
import com.restaiuranteboard.backend.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/configuracion")
public class ConfiguracionController {

    @Autowired private ConfiguracionSistemaRepository configNoSqlRepo;
    @Autowired private VerificationCodeRepository codeSqlRepo;
    @Autowired private EmailService emailService;

    @PostMapping("/enviar-verificacion")
    public ResponseEntity<?> enviarVerificacion(@RequestBody Map<String, String> request) {
        String email = request.get("emailSmtp");
        String pass = request.get("passwordSmtp");

        // Validaciones con respuesta JSON para el Modal
        if (email == null || !email.endsWith("@gmail.com")) 
            return ResponseEntity.badRequest().body(Map.of("message", "Debe ser un correo @gmail.com"));
        
        if (pass == null || pass.length() < 16) 
            return ResponseEntity.badRequest().body(Map.of("message", "La contraseña de aplicación de Google debe tener 16 caracteres"));

        String numCode = String.format("%06d", new Random().nextInt(999999));
        VerificationCode vCode = new VerificationCode();
        vCode.setEmail(email);
        vCode.setCode(numCode);
        vCode.setExpirationTime(LocalDateTime.now().plusMinutes(1)); 
        codeSqlRepo.save(vCode);

        try {
            emailService.enviarCodigoVerificacion(email, numCode, email, pass);
            return ResponseEntity.ok(Map.of("message", "Código enviado correctamente a " + email));
        } catch (Exception e) {
            // Error técnico del SMTP
            return ResponseEntity.internalServerError().body(Map.of("message", "No se pudo enviar el correo. Verifica tu contraseña de aplicación."));
        }
    }

    @PostMapping("/validar-y-guardar")
    public ResponseEntity<?> validarYGuardar(@RequestBody Map<String, Object> data) {
        String email = (String) data.get("emailSmtp");
        String codeIn = (String) data.get("codigoVerificacion");

        VerificationCode vCode = codeSqlRepo.findFirstByEmailAndUsedOrderByExpirationTimeDesc(email, false)
                .orElse(null);

        if (vCode == null || !vCode.getCode().equals(codeIn))
            return ResponseEntity.badRequest().body(Map.of("message", "El código ingresado es incorrecto."));
        
        if (LocalDateTime.now().isAfter(vCode.getExpirationTime()))
            return ResponseEntity.badRequest().body(Map.of("message", "El código ha expirado. Solicita uno nuevo."));

        vCode.setUsed(true);
        codeSqlRepo.save(vCode);

        ConfiguracionSistema config = new ConfiguracionSistema();
        config.setId("GLOBAL_CONFIG");
        config.setEmailSmtp(email);
        config.setPasswordSmtp((String) data.get("passwordSmtp"));
        config.setNombreNegocio((String) data.get("nombreNegocio"));
        config.setTelefonoNegocio((String) data.get("telefonoNegocio"));
        config.setTerminosCondiciones((String) data.get("terminosCondiciones"));
        config.setConfiguracionCompleta(true);

        configNoSqlRepo.save(config);

        return ResponseEntity.ok(Map.of("message", "¡Negocio configurado con éxito!"));
    }
}