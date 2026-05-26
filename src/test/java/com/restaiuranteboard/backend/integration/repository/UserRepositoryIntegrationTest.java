package com.restaiuranteboard.backend.integration.repository;

import com.restaiuranteboard.backend.integration.support.JpaIntegrationTestBase;
import com.restaiuranteboard.backend.model.sql.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserRepositoryIntegrationTest extends JpaIntegrationTestBase {

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        seedRoles();
    }

    @Test
    void saveAndFindByEmail_persistsUniqueEmail() {
        User saved = persistCliente("cliente@gmail.com", "12345678");

        User found = userRepository.findByEmail("cliente@gmail.com").orElseThrow();

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getDni()).isEqualTo("12345678");
        assertThat(found.getRole().getName()).isEqualTo("CLIENTE");
    }

    @Test
    void existsByEmail_returnsTrueWhenActiveUserExists() {
        persistCliente("duplicado@gmail.com", "87654321");

        assertThat(userRepository.existsByEmail("duplicado@gmail.com")).isTrue();
        assertThat(userRepository.existsByEmail("otro@gmail.com")).isFalse();
    }

    @Test
    void existsByDni_detectsRegisteredDni() {
        persistCliente("uno@gmail.com", "11111111");

        assertThat(userRepository.existsByDni("11111111")).isTrue();
        assertThat(userRepository.existsByDni("22222222")).isFalse();
    }
}
