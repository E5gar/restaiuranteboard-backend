package com.restaiuranteboard.backend.service;

import com.restaiuranteboard.backend.model.sql.MfaBackupCode;
import com.restaiuranteboard.backend.model.sql.User;
import com.restaiuranteboard.backend.repository.sql.MfaBackupCodeRepository;
import com.restaiuranteboard.backend.repository.sql.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class MfaService {

    private static final int BACKUP_CODE_COUNT = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final MfaBackupCodeRepository backupCodeRepository;
    private final TotpMfaService totpMfaService;
    private final PasswordEncoder passwordEncoder;

    public MfaService(
            UserRepository userRepository,
            MfaBackupCodeRepository backupCodeRepository,
            TotpMfaService totpMfaService,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.backupCodeRepository = backupCodeRepository;
        this.totpMfaService = totpMfaService;
        this.passwordEncoder = passwordEncoder;
    }

    public MfaSetupStartResult iniciarConfiguracion(User user) {
        if (user.isMfaEnabled()) {
            throw new IllegalStateException("La autenticación de doble factor ya está activa.");
        }
        String secret = totpMfaService.generateSecret();
        user.setMfaSecret(secret);
        user.setMfaEnabled(false);
        userRepository.save(user);
        String otpAuthUri = totpMfaService.buildOtpAuthUri(user.getEmail(), secret);
        return new MfaSetupStartResult(otpAuthUri, secret);
    }

    @Transactional
    public List<String> confirmarActivacion(User user, String code) {
        if (user.getMfaSecret() == null || user.getMfaSecret().isBlank()) {
            throw new IllegalStateException("Debes iniciar la configuración de doble factor primero.");
        }
        if (!totpMfaService.verifyCode(user.getMfaSecret(), code)) {
            throw new IllegalArgumentException("El código ingresado no es válido o ha expirado.");
        }
        user.setMfaEnabled(true);
        userRepository.save(user);
        backupCodeRepository.deleteByUserId(user.getId());
        return generarYGuardarCodigosRespaldo(user.getId());
    }

    @Transactional
    public void desactivar(User user, String password, String code) {
        if (!user.isMfaEnabled()) {
            throw new IllegalStateException("La autenticación de doble factor no está activa.");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("La contraseña es incorrecta.");
        }
        if (!totpMfaService.verifyCode(user.getMfaSecret(), code)) {
            throw new IllegalArgumentException("El código de autenticador no es válido.");
        }
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        userRepository.save(user);
        backupCodeRepository.deleteByUserId(user.getId());
    }

    @Transactional
    public void desactivarSinPassword(User user, String code, String backupCode) {
        if (!user.isMfaEnabled()) {
            throw new IllegalStateException("La autenticación de doble factor no está activa.");
        }
        boolean ok = false;
        if (code != null && !code.isBlank()) {
            ok = totpMfaService.verifyCode(user.getMfaSecret(), code);
        } else if (backupCode != null && !backupCode.isBlank()) {
            ok = verificarCodigoRespaldo(user, backupCode);
        }
        if (!ok) {
            throw new IllegalArgumentException("El código ingresado no es válido o ha expirado.");
        }
        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        userRepository.save(user);
        backupCodeRepository.deleteByUserId(user.getId());
    }

    public boolean requiereMfa(User user) {
        return user != null && user.isMfaEnabled() && user.getMfaSecret() != null && !user.getMfaSecret().isBlank();
    }

    public boolean verificarCodigoIngreso(User user, String totpCode) {
        return totpMfaService.verifyCode(user.getMfaSecret(), totpCode);
    }

    public boolean verificarCodigoRespaldo(User user, String backupCodePlain) {
        if (backupCodePlain == null || backupCodePlain.isBlank()) {
            return false;
        }
        String normalized = backupCodePlain.trim().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        List<MfaBackupCode> activos = backupCodeRepository.findByUserIdAndUsedAtIsNull(user.getId());
        for (MfaBackupCode row : activos) {
            if (passwordEncoder.matches(normalized, row.getCodeHash())) {
                row.setUsedAt(LocalDateTime.now());
                backupCodeRepository.save(row);
                return true;
            }
        }
        return false;
    }

    private List<String> generarYGuardarCodigosRespaldo(UUID userId) {
        List<String> plainCodes = new ArrayList<>();
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            String plain = generarCodigoRespaldo();
            plainCodes.add(plain);
            MfaBackupCode entity = new MfaBackupCode();
            entity.setUserId(userId);
            entity.setCodeHash(passwordEncoder.encode(plain));
            backupCodeRepository.save(entity);
        }
        return plainCodes;
    }

    private String generarCodigoRespaldo() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return sb.substring(0, 4) + "-" + sb.substring(4);
    }

    public record MfaSetupStartResult(String otpAuthUri, String secretPlain) {
    }
}
