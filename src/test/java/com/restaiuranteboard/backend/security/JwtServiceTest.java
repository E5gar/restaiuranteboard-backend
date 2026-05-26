package com.restaiuranteboard.backend.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String TEST_SECRET = "test-secret-key-minimum-32-characters-long!!";
    private static final long TEST_EXPIRATION_MS = 3_600_000L;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET, TEST_EXPIRATION_MS);
    }

    @Test
    void generateTokenAndParseClaims_roundtripPreservesClaims() {
        String token = jwtService.generateToken("user@test.com", "user-123", "CLIENTE");

        Claims claims = jwtService.parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo("user@test.com");
        assertThat(claims.get("userId", String.class)).isEqualTo("user-123");
        assertThat(claims.get("role", String.class)).isEqualTo("CLIENTE");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }
}
