package com.restaiuranteboard.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaiuranteboard.backend.repository.nosql.ConfiguracionSistemaRepository;
import com.restaiuranteboard.backend.repository.sql.RestaurantOrderRepository;
import com.restaiuranteboard.backend.repository.sql.UserRepository;
import com.restaiuranteboard.backend.repository.sql.VerificationCodeRepository;
import com.restaiuranteboard.backend.service.CuentaEliminacionService;
import com.restaiuranteboard.backend.service.EmailService;
import com.restaiuranteboard.backend.service.GoogleTokenVerifierService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PerfilController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(PerfilController.class)
class PerfilControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private RestaurantOrderRepository restaurantOrderRepository;

    @MockitoBean
    private ConfiguracionSistemaRepository configuracionSistemaRepository;

    @MockitoBean
    private VerificationCodeRepository verificationCodeRepository;

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private CuentaEliminacionService cuentaEliminacionService;

    @MockitoBean
    private GoogleTokenVerifierService googleTokenVerifierService;

    @Test
    void eliminarCuenta_returns401WithoutAuthenticatedUser() throws Exception {
        mockMvc.perform(post("/api/perfil/me/eliminar-cuenta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("password", "Secret1@"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("No autorizado."));
    }
}
