package com.restaiuranteboard.backend.controller;

import com.restaiuranteboard.backend.dto.CajaOrdenListaItem;
import com.restaiuranteboard.backend.service.PedidoCajaService;
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

@WebMvcTest(controllers = PedidoCajaController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(PedidoCajaController.class)
class PedidoCajaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PedidoCajaService pedidoCajaService;

    @Test
    void pendientes_returns400WhenProcessorUserIdInvalid() throws Exception {
        mockMvc.perform(get("/api/pedidos/caja/pendientes").param("processorUserId", "invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pendientes_returns200WithValidandoPagoOrders() throws Exception {
        UUID processorId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        List<CajaOrdenListaItem> items = List.of(
                new CajaOrdenListaItem(
                        orderId.toString(),
                        "2026-05-26T10:00:00",
                        "Cliente Test",
                        "45.50"
                )
        );

        when(pedidoCajaService.listarPendientesValidacion(any())).thenReturn(items);

        mockMvc.perform(get("/api/pedidos/caja/pendientes").param("processorUserId", processorId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(orderId.toString()))
                .andExpect(jsonPath("$[0].total").value("45.50"));
    }
}
