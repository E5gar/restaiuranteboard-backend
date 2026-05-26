package com.restaiuranteboard.backend.controller;

import com.restaiuranteboard.backend.dto.CarritoResponse;
import com.restaiuranteboard.backend.service.ShoppingCartService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ShoppingCartController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(ShoppingCartController.class)
class ShoppingCartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShoppingCartService shoppingCartService;

    @Test
    void obtener_returns400WhenUserInvalid() throws Exception {
        when(shoppingCartService.obtenerCarrito(eq("not-a-uuid")))
                .thenThrow(new IllegalArgumentException("userId inválido."));

        mockMvc.perform(get("/api/carrito").param("userId", "not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("userId inválido."));
    }

    @Test
    void obtener_returns200WithEmptyCart() throws Exception {
        String userId = UUID.randomUUID().toString();
        CarritoResponse cart = new CarritoResponse(List.of(), List.of());

        when(shoppingCartService.obtenerCarrito(userId)).thenReturn(cart);

        mockMvc.perform(get("/api/carrito").param("userId", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items").isEmpty());
    }
}
