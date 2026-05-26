package com.restaiuranteboard.backend.integration.repository;

import com.restaiuranteboard.backend.integration.support.JpaIntegrationTestBase;
import com.restaiuranteboard.backend.model.sql.IpLoginAttempt;
import com.restaiuranteboard.backend.repository.sql.IpLoginAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class IpLoginAttemptRepositoryIntegrationTest extends JpaIntegrationTestBase {

    @Autowired
    private IpLoginAttemptRepository ipLoginAttemptRepository;

    @BeforeEach
    void setUp() {
        ipLoginAttemptRepository.deleteAll();
    }

    @Test
    void saveAndFindByIpAddress_persistsFailedAttempts() {
        IpLoginAttempt attempt = new IpLoginAttempt();
        attempt.setIpAddress("192.168.1.50");
        attempt.setFailedAttempts(2);
        attempt.setLastFailedAt(LocalDateTime.now());
        ipLoginAttemptRepository.save(attempt);

        IpLoginAttempt found = ipLoginAttemptRepository.findByIpAddress("192.168.1.50").orElseThrow();

        assertThat(found.getFailedAttempts()).isEqualTo(2);
        assertThat(found.getBlockedUntil()).isNull();
    }

    @Test
    void update_blockedUntilPersistsForBruteForce() {
        IpLoginAttempt attempt = new IpLoginAttempt();
        attempt.setIpAddress("10.0.0.99");
        attempt.setFailedAttempts(3);
        attempt.setBlockedUntil(LocalDateTime.now().plusHours(1));
        ipLoginAttemptRepository.save(attempt);

        IpLoginAttempt found = ipLoginAttemptRepository.findByIpAddress("10.0.0.99").orElseThrow();

        assertThat(found.getFailedAttempts()).isEqualTo(3);
        assertThat(found.getBlockedUntil()).isAfter(LocalDateTime.now());
    }
}
