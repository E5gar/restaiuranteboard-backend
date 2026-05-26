package com.restaiuranteboard.backend.integration.repository;

import com.restaiuranteboard.backend.integration.support.JpaIntegrationTestBase;
import com.restaiuranteboard.backend.model.sql.User;
import com.restaiuranteboard.backend.model.sql.VerificationCode;
import com.restaiuranteboard.backend.repository.sql.VerificationCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRegistrationFlowIntegrationTest extends JpaIntegrationTestBase {

    @Autowired
    private VerificationCodeRepository verificationCodeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        verificationCodeRepository.deleteAll();
        seedRoles();
    }

    @Test
    void firstRegisteredUser_persistsAsAdminWithEncryptedPassword() {
        VerificationCode code = new VerificationCode();
        code.setEmail("admin@gmail.com");
        code.setCode("123456");
        code.setExpirationTime(LocalDateTime.now().plusMinutes(1));
        code.setUsed(false);
        verificationCodeRepository.save(code);

        User user = new User();
        user.setEmail("admin@gmail.com");
        user.setDni("12345678");
        user.setFullName("Ana Admin");
        user.setPhone("912345678");
        user.setAddress("Av. Principal 1");
        user.setPassword(passwordEncoder.encode("Admin1@x"));
        user.setFirstLogin(false);
        user.setDeleted(false);
        user.setRole(userRepository.count() == 0 ? roleAdmin : roleCliente);
        userRepository.save(user);

        code.setUsed(true);
        verificationCodeRepository.save(code);

        User saved = userRepository.findByEmail("admin@gmail.com").orElseThrow();
        assertThat(saved.getRole().getName()).isEqualTo("ADMIN");
        assertThat(passwordEncoder.matches("Admin1@x", saved.getPassword())).isTrue();
        assertThat(verificationCodeRepository
                .findFirstByEmailAndUsedOrderByExpirationTimeDesc("admin@gmail.com", false))
                .isEmpty();
    }

    @Test
    void secondRegisteredUser_persistsAsCliente() {
        persistCliente("existente@gmail.com", "87654321");

        VerificationCode code = new VerificationCode();
        code.setEmail("nuevo@gmail.com");
        code.setCode("654321");
        code.setExpirationTime(LocalDateTime.now().plusMinutes(1));
        code.setUsed(false);
        verificationCodeRepository.save(code);

        User user = new User();
        user.setEmail("nuevo@gmail.com");
        user.setDni("11223344");
        user.setFullName("Pedro Cliente");
        user.setPhone("987654321");
        user.setAddress("Calle 2");
        user.setPassword(passwordEncoder.encode("Cliente1@"));
        user.setFirstLogin(false);
        user.setDeleted(false);
        user.setRole(userRepository.count() == 0 ? roleAdmin : roleCliente);
        userRepository.save(user);

        User saved = userRepository.findByEmail("nuevo@gmail.com").orElseThrow();
        assertThat(saved.getRole().getName()).isEqualTo("CLIENTE");
        assertThat(userRepository.count()).isEqualTo(2);
    }
}
