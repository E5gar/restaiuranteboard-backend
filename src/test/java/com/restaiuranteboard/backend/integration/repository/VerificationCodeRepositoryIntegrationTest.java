package com.restaiuranteboard.backend.integration.repository;

import com.restaiuranteboard.backend.integration.support.JpaIntegrationTestBase;
import com.restaiuranteboard.backend.model.sql.VerificationCode;
import com.restaiuranteboard.backend.repository.sql.VerificationCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class VerificationCodeRepositoryIntegrationTest extends JpaIntegrationTestBase {

    @Autowired
    private VerificationCodeRepository verificationCodeRepository;

    @BeforeEach
    void setUp() {
        verificationCodeRepository.deleteAll();
    }

    @Test
    void findFirstByEmailAndUsedOrderByExpirationTimeDesc_returnsLatestValidCode() {
        VerificationCode old = new VerificationCode();
        old.setEmail("admin@gmail.com");
        old.setCode("111111");
        old.setExpirationTime(LocalDateTime.now().plusMinutes(1));
        old.setUsed(true);
        verificationCodeRepository.save(old);

        VerificationCode current = new VerificationCode();
        current.setEmail("admin@gmail.com");
        current.setCode("654321");
        current.setExpirationTime(LocalDateTime.now().plusMinutes(1));
        current.setUsed(false);
        verificationCodeRepository.save(current);

        Optional<VerificationCode> found = verificationCodeRepository
                .findFirstByEmailAndUsedOrderByExpirationTimeDesc("admin@gmail.com", false);

        assertThat(found).isPresent();
        assertThat(found.get().getCode()).isEqualTo("654321");
    }

    @Test
    void save_marksCodeAsUsedAfterRegistration() {
        VerificationCode code = new VerificationCode();
        code.setEmail("nuevo@gmail.com");
        code.setCode("123456");
        code.setExpirationTime(LocalDateTime.now().plusMinutes(1));
        code.setUsed(false);
        VerificationCode saved = verificationCodeRepository.save(code);

        saved.setUsed(true);
        verificationCodeRepository.save(saved);

        Optional<VerificationCode> active = verificationCodeRepository
                .findFirstByEmailAndUsedOrderByExpirationTimeDesc("nuevo@gmail.com", false);

        assertThat(active).isEmpty();
    }
}
