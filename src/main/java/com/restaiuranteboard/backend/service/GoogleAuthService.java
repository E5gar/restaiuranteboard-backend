package com.restaiuranteboard.backend.service;

import com.restaiuranteboard.backend.dto.GoogleProfile;
import com.restaiuranteboard.backend.model.sql.Role;
import com.restaiuranteboard.backend.model.sql.User;
import com.restaiuranteboard.backend.repository.sql.RoleRepository;
import com.restaiuranteboard.backend.repository.sql.UserRepository;
import com.restaiuranteboard.backend.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GoogleAuthService {

    private static final long REGISTRATION_SESSION_TTL_SECONDS = 900;

    private final ConcurrentHashMap<String, PendingGoogleRegistration> pendingRegistrations = new ConcurrentHashMap<>();

    public static final String PROVIDER_LOCAL = "LOCAL";
    public static final String PROVIDER_GOOGLE = "GOOGLE";
    public static final String PROVIDER_BOTH = "BOTH";

    private final GoogleTokenVerifierService tokenVerifier;
    private final UserRepository userRepo;
    private final RoleRepository roleRepo;
    private final MfaService mfaService;
    private final JwtService jwtService;
    private final ShoppingCartService shoppingCartService;
    private final PasswordEncoder passwordEncoder;

    public GoogleAuthService(
            GoogleTokenVerifierService tokenVerifier,
            UserRepository userRepo,
            RoleRepository roleRepo,
            MfaService mfaService,
            JwtService jwtService,
            ShoppingCartService shoppingCartService,
            PasswordEncoder passwordEncoder
    ) {
        this.tokenVerifier = tokenVerifier;
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.mfaService = mfaService;
        this.jwtService = jwtService;
        this.shoppingCartService = shoppingCartService;
        this.passwordEncoder = passwordEncoder;
    }

    public ResponseEntity<?> login(Map<String, String> body) {
        if (!tokenVerifier.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "Inicio de sesión con Google no está configurado."));
        }
        Optional<GoogleProfile> profileOpt = tokenVerifier.resolveProfile(
                body.get("idToken"),
                body.get("code")
        );
        if (profileOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "No se pudo verificar la cuenta de Google."));
        }
        GoogleProfile profile = profileOpt.get();

        User user = userRepo.findByGoogleSub(profile.sub()).orElse(null);
        if (user == null) {
            user = userRepo.findByEmailIgnoreCase(profile.email()).orElse(null);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "message",
                        "No existe una cuenta con este correo. Regístrate primero con Google o con correo y contraseña."
                ));
            }
            if (user.isDeleted()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "La cuenta no está disponible."));
            }
            ResponseEntity<?> linkError = vincularGoogle(user, profile);
            if (linkError != null) {
                return linkError;
            }
        } else if (user.isDeleted()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "La cuenta no está disponible."));
        }

        return respuestaTrasIdentidad(user);
    }

    public ResponseEntity<?> iniciarSesionRegistro(Map<String, String> body) {
        if (!tokenVerifier.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "Registro con Google no está configurado."));
        }
        Optional<GoogleProfile> profileOpt = tokenVerifier.resolveProfile(
                body.get("idToken"),
                body.get("code")
        );
        if (profileOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "No se pudo verificar la cuenta de Google."));
        }
        GoogleProfile profile = profileOpt.get();
        limpiarSesionesExpiradas();
        String token = UUID.randomUUID().toString();
        pendingRegistrations.put(token, new PendingGoogleRegistration(profile, Instant.now().getEpochSecond()));
        return ResponseEntity.ok(Map.of(
                "registrationToken", token,
                "email", profile.email(),
                "givenName", profile.givenName() != null ? profile.givenName() : "",
                "familyName", profile.familyName() != null ? profile.familyName() : ""
        ));
    }

    public ResponseEntity<?> registrar(Map<String, String> body) {
        if (!tokenVerifier.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", "Registro con Google no está configurado."));
        }

        GoogleProfile profile;
        String registrationToken = trimToNull(body.get("registrationToken"));
        if (registrationToken != null) {
            limpiarSesionesExpiradas();
            PendingGoogleRegistration pending = pendingRegistrations.remove(registrationToken);
            if (pending == null || pending.isExpired(REGISTRATION_SESSION_TTL_SECONDS)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "message",
                        "La sesión de Google expiró. Vuelve a autorizar con Google."
                ));
            }
            profile = pending.profile();
        } else {
            Optional<GoogleProfile> profileOpt = tokenVerifier.resolveProfile(
                    body.get("idToken"),
                    body.get("code")
            );
            if (profileOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "No se pudo verificar la cuenta de Google."));
            }
            profile = profileOpt.get();
        }

        String fullName = trimToNull(body.get("fullName"));
        String dni = trimToNull(body.get("dni"));
        String phone = trimToNull(body.get("phone"));
        String address = trimToNull(body.get("address"));
        if (fullName == null || dni == null || phone == null || address == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Todos los campos son obligatorios."));
        }

        if (userRepo.existsByEmailIgnoreCase(profile.email())) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message",
                    "Ya existe una cuenta con este correo. Inicia sesión con Google o con tu contraseña."
            ));
        }
        if (userRepo.existsByDni(dni)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Ya existe un usuario con ese DNI."));
        }
        if (userRepo.existsByPhone(phone)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Ya existe un usuario con ese número de teléfono."));
        }
        Optional<User> subOwner = userRepo.findByGoogleSub(profile.sub());
        if (subOwner.isPresent()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "message",
                    "Esta cuenta de Google ya está registrada. Inicia sesión."
            ));
        }

        User user = new User();
        user.setEmail(profile.email());
        user.setFullName(fullName);
        user.setDni(dni);
        user.setPhone(phone);
        user.setAddress(address);
        user.setPassword(null);
        user.setGoogleSub(profile.sub());
        user.setAuthProvider(PROVIDER_GOOGLE);
        user.setFirstLogin(false);

        if (userRepo.count() == 0) {
            Role admin = roleRepo.findByName("ADMIN").orElseThrow();
            user.setRole(admin);
        } else {
            Role cliente = roleRepo.findByName("CLIENTE").orElseThrow();
            user.setRole(cliente);
        }

        userRepo.save(user);
        return ResponseEntity.ok(construirRespuestaLoginCompleta(user));
    }

    public static boolean tienePasswordLocal(User user) {
        String pwd = user.getPassword();
        return pwd != null && !pwd.isBlank();
    }

    public static boolean esSoloGoogle(User user) {
        String provider = user.getAuthProvider();
        if (provider == null || provider.isBlank()) {
            return !tienePasswordLocal(user);
        }
        return PROVIDER_GOOGLE.equals(provider) && !tienePasswordLocal(user);
    }

    private ResponseEntity<?> vincularGoogle(User user, GoogleProfile profile) {
        String existingSub = user.getGoogleSub();
        if (existingSub != null && !existingSub.isBlank() && !existingSub.equals(profile.sub())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "message",
                    "Este correo está vinculado a otra cuenta de Google."
            ));
        }
        user.setGoogleSub(profile.sub());
        if (tienePasswordLocal(user)) {
            user.setAuthProvider(PROVIDER_BOTH);
        } else {
            user.setAuthProvider(PROVIDER_GOOGLE);
        }
        userRepo.save(user);
        return null;
    }

    private ResponseEntity<?> respuestaTrasIdentidad(User user) {
        if (mfaService.requiereMfa(user)) {
            String mfaToken = jwtService.generateMfaPendingToken(
                    user.getEmail(),
                    user.getId().toString(),
                    user.getRole().getName()
            );
            return ResponseEntity.ok(Map.of(
                    "mfaRequired", true,
                    "mfaToken", mfaToken,
                    "email", user.getEmail()
            ));
        }
        return ResponseEntity.ok(construirRespuestaLoginCompleta(user));
    }

    private Map<String, Object> construirRespuestaLoginCompleta(User user) {
        String token = jwtService.generateToken(
                user.getEmail(),
                user.getId().toString(),
                user.getRole().getName()
        );
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", token);
        body.put("fullName", user.getFullName());
        body.put("role", user.getRole().getName());
        body.put("email", user.getEmail());
        body.put("firstLogin", false);
        body.put("darkMode", user.isDarkMode());
        body.put("userId", user.getId().toString());
        body.put("mfaRequired", false);
        ShoppingCartService.LoginCartPayload payload = shoppingCartService.loadSanitizeAndEnrich(user.getId().toString());
        body.put("cart", payload.cart());
        body.put("removedItems", payload.removedItems());
        return body;
    }

    public void aplicarPasswordTrasRecuperacion(User user, String rawPassword) {
        user.setPassword(passwordEncoder.encode(rawPassword));
        if (PROVIDER_GOOGLE.equals(user.getAuthProvider())) {
            user.setAuthProvider(PROVIDER_BOTH);
        } else if (user.getAuthProvider() == null || user.getAuthProvider().isBlank()) {
            user.setAuthProvider(PROVIDER_LOCAL);
        }
    }

    private void limpiarSesionesExpiradas() {
        long now = Instant.now().getEpochSecond();
        pendingRegistrations.entrySet().removeIf(e ->
                e.getValue().isExpiredAt(now, REGISTRATION_SESSION_TTL_SECONDS));
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private record PendingGoogleRegistration(GoogleProfile profile, long createdAtEpochSeconds) {
        boolean isExpired(long ttlSeconds) {
            return isExpiredAt(Instant.now().getEpochSecond(), ttlSeconds);
        }

        boolean isExpiredAt(long nowEpochSeconds, long ttlSeconds) {
            return nowEpochSeconds - createdAtEpochSeconds > ttlSeconds;
        }
    }
}
