package com.restaiuranteboard.backend.integration.support;

import com.restaiuranteboard.backend.JpaIntegrationTestApplication;
import com.restaiuranteboard.backend.model.sql.Role;
import com.restaiuranteboard.backend.model.sql.User;
import com.restaiuranteboard.backend.repository.sql.RoleRepository;
import com.restaiuranteboard.backend.repository.sql.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@ContextConfiguration(classes = JpaIntegrationTestApplication.class)
@ActiveProfiles("integration")
@TestPropertySource(locations = "classpath:application-integration.properties")
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(JpaIntegrationTestBase.TestConfig.class)
public abstract class JpaIntegrationTestBase {

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected RoleRepository roleRepository;

    protected Role roleCliente;
    protected Role roleAdmin;

    protected void seedRoles() {
        roleRepository.deleteAll();
        roleCliente = new Role();
        roleCliente.setName("CLIENTE");
        roleAdmin = new Role();
        roleAdmin.setName("ADMIN");
        roleRepository.save(roleCliente);
        roleRepository.save(roleAdmin);
    }

    protected User persistCliente(String email, String dni) {
        User u = new User();
        u.setEmail(email);
        u.setDni(dni);
        u.setFullName("Cliente Prueba");
        u.setPhone("912345678");
        u.setAddress("Av. Test 100");
        u.setPassword(new BCryptPasswordEncoder().encode("Secret1@"));
        u.setRole(roleCliente);
        u.setFirstLogin(false);
        u.setDeleted(false);
        return userRepository.save(u);
    }

    static class TestConfig {
        @org.springframework.context.annotation.Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }
}
