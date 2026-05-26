package com.restaiuranteboard.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaiuranteboard.backend.dto.CarritoResponse;
import com.restaiuranteboard.backend.model.sql.IpLoginAttempt;
import com.restaiuranteboard.backend.model.sql.User;
import com.restaiuranteboard.backend.repository.nosql.ConfiguracionSistemaRepository;
import com.restaiuranteboard.backend.repository.sql.IpLoginAttemptRepository;
import com.restaiuranteboard.backend.repository.sql.LoginAuditRepository;
import com.restaiuranteboard.backend.repository.sql.RoleRepository;
import com.restaiuranteboard.backend.repository.sql.UserRepository;
import com.restaiuranteboard.backend.repository.sql.VerificationCodeRepository;
import com.restaiuranteboard.backend.security.JwtService;
import com.restaiuranteboard.backend.service.EmailService;
import com.restaiuranteboard.backend.service.ShoppingCartService;
import com.restaiuranteboard.backend.support.TestEntities;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private UserRepository userRepo;

    @MockitoBean
    private RoleRepository roleRepo;

    @MockitoBean
    private VerificationCodeRepository codeRepo;

    @MockitoBean
    private IpLoginAttemptRepository ipLoginAttemptRepo;

    @MockitoBean
    private LoginAuditRepository loginAuditRepo;

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private ConfiguracionSistemaRepository configRepo;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private ShoppingCartService shoppingCartService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void checkAdmin_returnsHasAdminFalseWhenNoUsers() throws Exception {
        when(userRepo.count()).thenReturn(0L);

        mockMvc.perform(get("/api/auth/check-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAdmin").value(false));
    }

    @Test
    void checkAdmin_returnsHasAdminTrueWhenUsersExist() throws Exception {
        when(userRepo.count()).thenReturn(1L);

        mockMvc.perform(get("/api/auth/check-admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAdmin").value(true));
    }

    @Test
    void login_returns401WhenUserNotFound() throws Exception {
        when(ipLoginAttemptRepo.findByIpAddress(any())).thenReturn(Optional.empty());
        when(ipLoginAttemptRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepo.findByEmail("missing@test.com")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "missing@test.com",
                                "password", "Secret1@"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("El correo electrónico no existe."));

        verify(loginAuditRepo).save(any());
    }

    @Test
    void login_returns401WhenPasswordWrong() throws Exception {
        User user = TestEntities.userCliente();
        user.setFirstLogin(false);
        IpLoginAttempt attempt = TestEntities.ipLoginAttempt("127.0.0.1");

        when(ipLoginAttemptRepo.findByIpAddress(any())).thenReturn(Optional.of(attempt));
        when(ipLoginAttemptRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepo.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", user.getPassword())).thenReturn(false);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", user.getEmail(),
                                "password", "wrong"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Contraseña incorrecta."))
                .andExpect(jsonPath("$.failedAttempts").value(1))
                .andExpect(jsonPath("$.blocked").value(false));
    }

    @Test
    void login_returns423WhenIpBlocked() throws Exception {
        IpLoginAttempt blocked = TestEntities.ipLoginAttempt("127.0.0.1");
        blocked.setFailedAttempts(3);
        blocked.setBlockedUntil(LocalDateTime.now().plusMinutes(30));

        when(ipLoginAttemptRepo.findByIpAddress(any())).thenReturn(Optional.of(blocked));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", "cliente@test.com",
                                "password", "Secret1@"
                        ))))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.blocked").value(true))
                .andExpect(jsonPath("$.ipAddress").exists());
    }

    @Test
    void login_returns200WithTokenOnSuccess() throws Exception {
        User user = TestEntities.userCliente();
        user.setFirstLogin(false);
        IpLoginAttempt attempt = TestEntities.ipLoginAttempt("127.0.0.1");
        CarritoResponse cart = new CarritoResponse(List.of(), List.of());

        when(ipLoginAttemptRepo.findByIpAddress(any())).thenReturn(Optional.of(attempt));
        when(ipLoginAttemptRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepo.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Secret1@", user.getPassword())).thenReturn(true);
        when(jwtService.generateToken(eq(user.getEmail()), eq(user.getId().toString()), eq("CLIENTE")))
                .thenReturn("jwt-token");
        when(shoppingCartService.loadSanitizeAndEnrich(user.getId().toString()))
                .thenReturn(new ShoppingCartService.LoginCartPayload(cart, List.of()));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", user.getEmail(),
                                "password", "Secret1@"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.role").value("CLIENTE"));
    }
}
