package com.restaiuranteboard.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaiuranteboard.backend.service.CalificacionPedidoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CalificacionPedidoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(CalificacionPedidoController.class)
class CalificacionPedidoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private CalificacionPedidoService calificacionPedidoService;

    @Test
    void enviar_returns400WhenBodyIncomplete() throws Exception {
        mockMvc.perform(post("/api/pedidos/calificacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("stars", 5))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Datos incompletos."));
    }

    @Test
    void enviar_returns400WhenServiceRejects() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        doThrow(new IllegalArgumentException("Solo puedes calificar pedidos entregados."))
                .when(calificacionPedidoService).calificar(eq(userId), eq(orderId), eq(5), eq("Bueno"));

        mockMvc.perform(post("/api/pedidos/calificacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", userId.toString(),
                                "orderId", orderId.toString(),
                                "stars", 5,
                                "comment", "Bueno"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Solo puedes calificar pedidos entregados."));
    }

    @Test
    void enviar_returns200OnSuccess() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();

        mockMvc.perform(post("/api/pedidos/calificacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", userId.toString(),
                                "orderId", orderId.toString(),
                                "stars", 4,
                                "comment", "Excelente"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }
}
