package com.restaiuranteboard.backend.controller;

import com.restaiuranteboard.backend.dto.SeguimientoPedidoListasResponse;
import com.restaiuranteboard.backend.dto.SeguimientoPedidoResponse;
import com.restaiuranteboard.backend.service.SeguimientoPedidoService;
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

@WebMvcTest(controllers = SeguimientoPedidoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(SeguimientoPedidoController.class)
class SeguimientoPedidoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SeguimientoPedidoService seguimientoPedidoService;

    @Test
    void listas_returns400WhenUserIdInvalid() throws Exception {
        mockMvc.perform(get("/api/pedidos/seguimiento/listas").param("userId", "bad"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listas_returns200WithPendientesAndFinalizados() throws Exception {
        UUID userId = UUID.randomUUID();
        SeguimientoPedidoResponse pedido = new SeguimientoPedidoResponse(
                UUID.randomUUID().toString(),
                "VALIDANDO_PAGO",
                "2026-05-26T10:00:00",
                "50.00",
                "",
                "",
                false,
                List.of()
        );
        SeguimientoPedidoListasResponse response = new SeguimientoPedidoListasResponse(
                List.of(pedido),
                List.of()
        );

        when(seguimientoPedidoService.listar(any())).thenReturn(response);

        mockMvc.perform(get("/api/pedidos/seguimiento/listas").param("userId", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendientes").isArray())
                .andExpect(jsonPath("$.pendientes[0].estado").value("VALIDANDO_PAGO"))
                .andExpect(jsonPath("$.finalizados").isArray());
    }

    @Test
    void actual_returns400WhenNoOrders() throws Exception {
        UUID userId = UUID.randomUUID();
        when(seguimientoPedidoService.obtenerPedidoActual(userId))
                .thenThrow(new IllegalArgumentException("No tienes pedidos registrados."));

        mockMvc.perform(get("/api/pedidos/seguimiento/actual").param("userId", userId.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No tienes pedidos registrados."));
    }
}
