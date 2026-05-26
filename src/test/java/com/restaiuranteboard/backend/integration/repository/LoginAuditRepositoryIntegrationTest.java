package com.restaiuranteboard.backend.integration.repository;

import com.restaiuranteboard.backend.integration.support.JpaIntegrationTestBase;
import com.restaiuranteboard.backend.model.sql.LoginAudit;
import com.restaiuranteboard.backend.repository.sql.LoginAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAuditRepositoryIntegrationTest extends JpaIntegrationTestBase {

    @Autowired
    private LoginAuditRepository loginAuditRepository;

    @BeforeEach
    void setUp() {
        loginAuditRepository.deleteAll();
        userRepository.deleteAll();
        seedRoles();
        persistCliente("cliente@gmail.com", "12345678");
    }

    @Test
    void save_persistsSuccessfulLoginAudit() {
        LoginAudit audit = new LoginAudit();
        audit.setUserEmail("cliente@gmail.com");
        audit.setIpAddress("192.168.0.10");
        audit.setStatus("SUCCESS");
        audit.setAttemptedAt(LocalDateTime.now());
        loginAuditRepository.save(audit);

        List<LoginAudit> rows = loginAuditRepository.findAll();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getStatus()).isEqualTo("SUCCESS");
        assertThat(rows.get(0).getIpAddress()).isEqualTo("192.168.0.10");
    }

    @Test
    void save_persistsFailedLoginAudit() {
        LoginAudit audit = new LoginAudit();
        audit.setUserEmail("cliente@gmail.com");
        audit.setIpAddress("10.0.0.5");
        audit.setStatus("FAILED");
        audit.setFailureReason("INVALID_PASSWORD");
        audit.setAttemptedAt(LocalDateTime.now());
        loginAuditRepository.save(audit);

        LoginAudit found = loginAuditRepository.findAll().get(0);
        assertThat(found.getStatus()).isEqualTo("FAILED");
        assertThat(found.getFailureReason()).isEqualTo("INVALID_PASSWORD");
    }
}
