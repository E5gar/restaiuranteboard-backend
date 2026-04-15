package com.restaiuranteboard.backend.controller;

import com.restaiuranteboard.backend.model.sql.*;
import com.restaiuranteboard.backend.repository.sql.*;
import com.restaiuranteboard.backend.service.EmailService;
import com.restaiuranteboard.backend.model.nosql.ConfiguracionSistema;
import com.restaiuranteboard.backend.repository.nosql.ConfiguracionSistemaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired private UserRepository userRepo;
    @Autowired private RoleRepository roleRepo;
    @Autowired private VerificationCodeRepository codeRepo;
    @Autowired private EmailService emailService;
    @Autowired private ConfiguracionSistemaRepository configRepo;
    @Autowired private PasswordEncoder passwordEncoder;

    // ==========================================
    // 1. CHEQUEO INICIAL (Para el Frontend)
    // ==========================================
    @GetMapping("/check-admin")
    public ResponseEntity<?> checkAdmin() {
        boolean hasAdmin = userRepo.count() > 0;
        return ResponseEntity.ok(Map.of("hasAdmin", hasAdmin));
    }

    // ==========================================
    // 2. REGISTRO (Admin y Cliente)
    // ==========================================
    @PostMapping("/enviar-codigo-registro")
    public ResponseEntity<?> enviarCodigo(@RequestBody Map<String, String> request) {
        String email = request.get("email");

        if (userRepo.existsByEmail(email)) 
            return ResponseEntity.badRequest().body(Map.of("message", "Este email ya está registrado."));
        
        ConfiguracionSistema config = configRepo.findById("GLOBAL_CONFIG").orElse(null);
        if (config == null) 
            return ResponseEntity.badRequest().body(Map.of("message", "El sistema no ha sido configurado aún."));

        return generarYEnviarCodigo(email, config);
    }

    @PostMapping("/registrar")
    public ResponseEntity<?> registrarUsuario(@RequestBody Map<String, String> data) {
        String email = data.get("email");
        String codeIn = data.get("codigo");

        VerificationCode vCode = codeRepo.findFirstByEmailAndUsedOrderByExpirationTimeDesc(email, false).orElse(null);
        if (vCode == null || !vCode.getCode().equals(codeIn)) 
            return ResponseEntity.badRequest().body(Map.of("message", "Código incorrecto."));
        
        if (LocalDateTime.now().isAfter(vCode.getExpirationTime()))
            return ResponseEntity.badRequest().body(Map.of("message", "El código ha expirado."));

        String password = data.get("password");
        if (!password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@!¡¿?#$%/&])[A-Za-z\\d@!¡¿?#$%/&]{8,}$")) {
            return ResponseEntity.badRequest().body(Map.of("message", "La contraseña no cumple los requisitos de seguridad."));
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setDni(data.get("dni"));
        user.setFullName(data.get("fullName"));
        user.setPhone(data.get("phone"));
        user.setAddress(data.get("address"));
        user.setFirstLogin(false); // Porque el usuario (Admin o Cliente) acaba de configurar su propia clave

        if (userRepo.count() == 0) {
            user.setRole(roleRepo.findByName("ADMIN").get());
            user.setFirstLogin(false);
        } else {
            user.setRole(roleRepo.findByName("CLIENTE").get());
            user.setFirstLogin(false);
        }

        userRepo.save(user);
        vCode.setUsed(true);
        codeRepo.save(vCode);

        return ResponseEntity.ok(Map.of("message", "Cuenta creada exitosamente."));
    }

    // ==========================================
    // 3. LOGIN UNIVERSAL (HU-01 a 05 y HU-21)
    // ==========================================
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        String email = credentials.get("email");
        String password = credentials.get("password");

        User user = userRepo.findByEmail(email).orElse(null);
        if (user == null || user.isDeleted()) {
            return ResponseEntity.status(401).body(Map.of("message", "El correo electrónico no existe."));
        }

        // Lógica HU-21: Si es empleado y entra por primera vez, no pedimos contraseña real.
        // Solo verificamos que puso su correo, el Frontend lo mandará a cambiar la clave.
        if (user.isFirstLogin()) {
            return ResponseEntity.ok(Map.of(
                "email", user.getEmail(), 
                "firstLogin", true, 
                "role", user.getRole().getName()
            ));
        }

        // Si es un usuario normal (Admin, Cliente o Empleado ya confirmado), verificamos la clave
        if (!passwordEncoder.matches(password, user.getPassword())) {
            return ResponseEntity.status(401).body(Map.of("message", "Contraseña incorrecta."));
        }

        return ResponseEntity.ok(Map.of(
            "fullName", user.getFullName(),
            "role", user.getRole().getName(),
            "email", user.getEmail(),
            "firstLogin", false
        ));
    }

    // ==========================================
    // 4. CREACIÓN Y CONFIRMACIÓN DE EMPLEADOS (HU-20 y HU-21)
    // ==========================================
    @PostMapping("/crear-empleado")
    public ResponseEntity<?> crearEmpleado(@RequestBody Map<String, String> data) {
        if (userRepo.existsByEmail(data.get("email"))) return ResponseEntity.badRequest().body(Map.of("message", "Email ya registrado."));
        if (userRepo.existsByDni(data.get("dni"))) return ResponseEntity.badRequest().body(Map.of("message", "DNI ya registrado."));
        if (userRepo.existsByPhone(data.get("phone"))) return ResponseEntity.badRequest().body(Map.of("message", "Teléfono ya registrado."));

        User user = new User();
        user.setEmail(data.get("email"));
        user.setDni(data.get("dni"));
        user.setFullName(data.get("fullName"));
        user.setPhone(data.get("phone"));
        user.setAddress(data.get("address"));
        user.setRole(roleRepo.findByName(data.get("role")).orElseThrow());
        
        // Se le asigna una clave aleatoria basura, la cambiará en el primer login
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString())); 
        user.setFirstLogin(true); // HU-21: Marcar para que configure su clave al entrar

        userRepo.save(user);
        return ResponseEntity.ok(Map.of("message", "Empleado creado. Debe iniciar sesión con su correo para activar la cuenta."));
    }

    @PostMapping("/enviar-codigo-empleado")
    public ResponseEntity<?> enviarCodigoEmpleado(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        User user = userRepo.findByEmail(email).orElse(null);
        
        if (user == null || !user.isFirstLogin()) 
            return ResponseEntity.badRequest().body(Map.of("message", "Acción no permitida para este usuario."));
        
        ConfiguracionSistema config = configRepo.findById("GLOBAL_CONFIG").orElse(null);
        if (config == null) return ResponseEntity.internalServerError().body(Map.of("message", "El sistema no está configurado."));

        return generarYEnviarCodigo(email, config);
    }

    @PostMapping("/confirmar-empleado")
    public ResponseEntity<?> confirmarEmpleado(@RequestBody Map<String, String> data) {
        String email = data.get("email");
        String codeIn = data.get("codigo");
        String password = data.get("password");

        VerificationCode vCode = codeRepo.findFirstByEmailAndUsedOrderByExpirationTimeDesc(email, false).orElse(null);
        
        if (vCode == null || !vCode.getCode().equals(codeIn)) return ResponseEntity.badRequest().body(Map.of("message", "Código incorrecto."));
        if (LocalDateTime.now().isAfter(vCode.getExpirationTime())) return ResponseEntity.badRequest().body(Map.of("message", "Código expirado."));

        if (!password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@!¡¿?#$%/&])[A-Za-z\\d@!¡¿?#$%/&]{8,}$")) 
            return ResponseEntity.badRequest().body(Map.of("message", "La contraseña es débil."));

        User user = userRepo.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.badRequest().body(Map.of("message", "Usuario no encontrado."));

        user.setPassword(passwordEncoder.encode(password));
        user.setFirstLogin(false); // Cuenta activada
        userRepo.save(user);

        vCode.setUsed(true);
        codeRepo.save(vCode);

        return ResponseEntity.ok(Map.of("message", "Cuenta activada con éxito. Ya puedes ingresar."));
    }

    // ==========================================
    // 5. RECUPERACIÓN DE CONTRASEÑA
    // ==========================================
    @PostMapping("/enviar-codigo-recuperacion")
    public ResponseEntity<?> enviarRecuperacion(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (!userRepo.existsByEmail(email)) return ResponseEntity.badRequest().body(Map.of("message", "Correo no registrado."));
        
        ConfiguracionSistema config = configRepo.findById("GLOBAL_CONFIG").orElse(null);
        if (config == null) return ResponseEntity.internalServerError().body(Map.of("message", "El sistema no está configurado."));

        return generarYEnviarCodigo(email, config);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> data) {
        String email = data.get("email");
        String codeIn = data.get("codigo");
        String newPassword = data.get("newPassword");

        VerificationCode vCode = codeRepo.findFirstByEmailAndUsedOrderByExpirationTimeDesc(email, false).orElse(null);
        if (vCode == null || !vCode.getCode().equals(codeIn)) return ResponseEntity.badRequest().body(Map.of("message", "Código incorrecto."));
        if (LocalDateTime.now().isAfter(vCode.getExpirationTime())) return ResponseEntity.badRequest().body(Map.of("message", "El código ha expirado."));
        if (!newPassword.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@!¡¿?#$%/&])[A-Za-z\\d@!¡¿?#$%/&]{8,}$")) return ResponseEntity.badRequest().body(Map.of("message", "La contraseña no cumple los requisitos de seguridad."));

        User user = userRepo.findByEmail(email).orElse(null);
        if (user == null) return ResponseEntity.badRequest().body(Map.of("message", "Usuario no encontrado."));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepo.save(user);
        vCode.setUsed(true);
        codeRepo.save(vCode);

        return ResponseEntity.ok(Map.of("message", "Contraseña actualizada exitosamente."));
    }

    // ==========================================
    // MÉTODO AUXILIAR PARA ENVÍO DE CORREOS
    // ==========================================
    private ResponseEntity<?> generarYEnviarCodigo(String email, ConfiguracionSistema config) {
        String code = String.format("%06d", new Random().nextInt(999999));
        VerificationCode vCode = new VerificationCode();
        vCode.setEmail(email);
        vCode.setCode(code);
        vCode.setExpirationTime(LocalDateTime.now().plusMinutes(1));
        codeRepo.save(vCode);

        try {
            emailService.enviarCodigoVerificacion(email, code, config.getEmailSmtp(), config.getPasswordSmtp());
            return ResponseEntity.ok(Map.of("message", "Código enviado al correo."));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Error al enviar el correo."));
        }
    }
}