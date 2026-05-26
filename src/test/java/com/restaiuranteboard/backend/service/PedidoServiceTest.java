package com.restaiuranteboard.backend.service;

import com.restaiuranteboard.backend.model.nosql.ShoppingCart;
import com.restaiuranteboard.backend.model.sql.User;
import com.restaiuranteboard.backend.repository.nosql.ProductoRepository;
import com.restaiuranteboard.backend.repository.nosql.ShoppingCartRepository;
import com.restaiuranteboard.backend.repository.sql.OrderItemRepository;
import com.restaiuranteboard.backend.repository.sql.RestaurantOrderRepository;
import com.restaiuranteboard.backend.repository.sql.UserRepository;
import com.restaiuranteboard.backend.support.TestEntities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private ShoppingCartRepository shoppingCartRepository;

    @Mock
    private ShoppingCartService shoppingCartService;

    @Mock
    private RestaurantOrderRepository restaurantOrderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ContextoInteligenciaService contextoInteligenciaService;

    @Mock
    private UserInteractionService userInteractionService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private SeguimientoClientePushService seguimientoClientePushService;

    private PedidoService service;

    private User client;

    @BeforeEach
    void setUp() {
        service = new PedidoService();
        ReflectionTestUtils.setField(service, "userRepository", userRepository);
        ReflectionTestUtils.setField(service, "productoRepository", productoRepository);
        ReflectionTestUtils.setField(service, "shoppingCartRepository", shoppingCartRepository);
        ReflectionTestUtils.setField(service, "shoppingCartService", shoppingCartService);
        ReflectionTestUtils.setField(service, "restaurantOrderRepository", restaurantOrderRepository);
        ReflectionTestUtils.setField(service, "orderItemRepository", orderItemRepository);
        ReflectionTestUtils.setField(service, "contextoInteligenciaService", contextoInteligenciaService);
        ReflectionTestUtils.setField(service, "userInteractionService", userInteractionService);
        ReflectionTestUtils.setField(service, "messagingTemplate", messagingTemplate);
        ReflectionTestUtils.setField(service, "seguimientoClientePushService", seguimientoClientePushService);
        client = TestEntities.userCliente();
    }

    @Test
    void crearPedidoConComprobante_throwsWhenComprobanteMissing() {
        assertThatThrownBy(() -> service.crearPedidoConComprobante(client.getId(), null, "image/png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Debes adjuntar el comprobante de pago.");

        verify(userRepository, never()).findById(any());
    }

    @Test
    void crearPedidoConComprobante_throwsWhenContentTypeInvalid() {
        byte[] data = new byte[]{1, 2, 3};

        assertThatThrownBy(() -> service.crearPedidoConComprobante(client.getId(), data, "application/pdf"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Solo se permiten imágenes en formato JPG o PNG.");
    }

    @Test
    void crearPedidoConComprobante_throwsWhenCartEmpty() {
        byte[] data = new byte[]{1, 2, 3};
        ShoppingCart cart = new ShoppingCart();
        cart.setUserId(client.getId().toString());
        cart.setItems(new ArrayList<>());

        when(userRepository.findById(client.getId())).thenReturn(Optional.of(client));
        when(shoppingCartService.getOrCreate(client.getId().toString())).thenReturn(cart);
        when(shoppingCartService.sanitizeAndPersist(cart)).thenReturn(new ArrayList<>());

        assertThatThrownBy(() -> service.crearPedidoConComprobante(client.getId(), data, "image/png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tu carrito está vacío o los productos ya no están disponibles.");

        verify(restaurantOrderRepository, never()).save(any());
    }
}
