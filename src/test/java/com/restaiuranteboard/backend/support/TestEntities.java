package com.restaiuranteboard.backend.support;

import com.restaiuranteboard.backend.model.sql.IpLoginAttempt;
import com.restaiuranteboard.backend.model.sql.Role;
import com.restaiuranteboard.backend.model.sql.User;
import com.restaiuranteboard.backend.model.sql.VerificationCode;

import java.time.LocalDateTime;
import java.util.UUID;

public final class TestEntities {

    private TestEntities() {
    }

    public static Role role(String name) {
        Role role = new Role();
        role.setId(1);
        role.setName(name);
        return role;
    }

    public static Role roleCliente() {
        return role("CLIENTE");
    }

    public static Role roleAdmin() {
        return role("ADMIN");
    }

    public static User userCliente() {
        return user("cliente@test.com", roleCliente(), false);
    }

    public static User userCliente(UUID id) {
        User user = userCliente();
        user.setId(id);
        return user;
    }

    public static User user(String email, Role role, boolean firstLogin) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPassword("$2a$10$encodedPasswordHash");
        user.setRole(role);
        user.setDni("12345678");
        user.setFullName("Test User");
        user.setPhone("912345678");
        user.setAddress("Calle Test 1");
        user.setFirstLogin(firstLogin);
        user.setDeleted(false);
        return user;
    }

    public static VerificationCode verificationCode(String email, String code) {
        VerificationCode vCode = new VerificationCode();
        vCode.setId(1L);
        vCode.setEmail(email);
        vCode.setCode(code);
        vCode.setExpirationTime(LocalDateTime.now().plusMinutes(5));
        vCode.setUsed(false);
        return vCode;
    }

    public static IpLoginAttempt ipLoginAttempt(String ipAddress) {
        IpLoginAttempt attempt = new IpLoginAttempt();
        attempt.setId(1);
        attempt.setIpAddress(ipAddress);
        attempt.setFailedAttempts(0);
        return attempt;
    }
}
