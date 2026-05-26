package com.restaiuranteboard.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaiuranteboard.backend.repository.nosql.ConfiguracionSistemaRepository;
import com.restaiuranteboard.backend.repository.sql.VerificationCodeRepository;
import com.restaiuranteboard.backend.service.EmailService;
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

@WebMvcTest(controllers = ConfiguracionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ConfiguracionController.class)
class ConfiguracionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ConfiguracionSistemaRepository configNoSqlRepo;

    @MockitoBean
    private VerificationCodeRepository codeSqlRepo;

    @MockitoBean
    private EmailService emailService;

    @Test
    void enviarVerificacion_returns400WhenEmailNotGmail() throws Exception {
        mockMvc.perform(post("/api/configuracion/enviar-verificacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "emailSmtp", "admin@outlook.com",
                                "passwordSmtp", "1234567890123456"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Debe ser un correo @gmail.com"));
    }

    @Test
    void enviarVerificacion_returns400WhenPasswordShorterThan16Chars() throws Exception {
        mockMvc.perform(post("/api/configuracion/enviar-verificacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "emailSmtp", "admin@gmail.com",
                                "passwordSmtp", "short"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La contraseña de aplicación de Google debe tener 16 caracteres"));
    }
}
