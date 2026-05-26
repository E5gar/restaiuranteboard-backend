package com.restaiuranteboard.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaiuranteboard.backend.model.sql.Inventory;
import com.restaiuranteboard.backend.repository.nosql.ProductoRepository;
import com.restaiuranteboard.backend.repository.sql.InventoryMovementRepository;
import com.restaiuranteboard.backend.repository.sql.InventoryRepository;
import com.restaiuranteboard.backend.repository.sql.OrderItemRepository;
import com.restaiuranteboard.backend.repository.sql.RecipeRepository;
import com.restaiuranteboard.backend.service.AiModelService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CatalogoController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(CatalogoController.class)
class CatalogoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ProductoRepository productoMongoRepo;

    @MockitoBean
    private InventoryRepository inventorySqlRepo;

    @MockitoBean
    private RecipeRepository recipeSqlRepo;

    @MockitoBean
    private InventoryMovementRepository inventoryMovementRepo;

    @MockitoBean
    private OrderItemRepository orderItemRepo;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    @MockitoBean
    private AiModelService aiModelService;

    @Test
    void guardarIngrediente_returns400WhenNameMissing() throws Exception {
        Inventory item = new Inventory();
        item.setCategory("Verduras");
        item.setUnit("GR");
        item.setStockQuantity(10.0);
        item.setPrice(1.5);
        item.setImageBase64("data:image/png;base64,abc");

        mockMvc.perform(post("/api/catalogo/ingredientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(item)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El nombre del insumo es obligatorio."));
    }

    @Test
    void guardarProducto_returns400WhenRequestInvalid() throws Exception {
        mockMvc.perform(post("/api/catalogo/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Solicitud inválida."));
    }
}
