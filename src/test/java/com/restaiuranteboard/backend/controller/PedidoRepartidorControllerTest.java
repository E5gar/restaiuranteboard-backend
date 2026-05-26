package com.restaiuranteboard.backend.controller;

import com.restaiuranteboard.backend.dto.RepartidorOrdenCard;
import com.restaiuranteboard.backend.service.PedidoRepartidorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PedidoRepartidorController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(PedidoRepartidorController.class)
class PedidoRepartidorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PedidoRepartidorService pedidoRepartidorService;

    @Test
    void tablero_returns400WhenUserIdInvalid() throws Exception {
        mockMvc.perform(get("/api/pedidos/repartidor/tablero").param("userId", "bad"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tablero_returns200WithOrders() throws Exception {
        UUID userId = UUID.randomUUID();
        RepartidorOrdenCard card = new RepartidorOrdenCard(
                UUID.randomUUID().toString(),
                "LISTO_REPARTO",
                "2026-05-26T10:00:00",
                "",
                "",
                "Cliente",
                "Calle 1",
                null
        );

        when(pedidoRepartidorService.listarTablero(any())).thenReturn(List.of(card));

        mockMvc.perform(get("/api/pedidos/repartidor/tablero").param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("LISTO_REPARTO"));
    }
}
