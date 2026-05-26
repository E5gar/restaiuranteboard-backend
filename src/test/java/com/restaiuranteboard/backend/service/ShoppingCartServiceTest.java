package com.restaiuranteboard.backend.service;

import com.restaiuranteboard.backend.model.nosql.CartItemMongo;
import com.restaiuranteboard.backend.model.nosql.Producto;
import com.restaiuranteboard.backend.model.nosql.ShoppingCart;
import com.restaiuranteboard.backend.model.sql.User;
import com.restaiuranteboard.backend.repository.nosql.ProductoRepository;
import com.restaiuranteboard.backend.repository.nosql.ShoppingCartRepository;
import com.restaiuranteboard.backend.repository.sql.UserRepository;
import com.restaiuranteboard.backend.support.TestEntities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShoppingCartServiceTest {

    @Mock
    private ShoppingCartRepository shoppingCartRepository;

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserInteractionService userInteractionService;

    @Mock
    private AiModelService aiModelService;

    private ShoppingCartService service;

    private final String userId = UUID.randomUUID().toString();
    private final String productId = "prod-1";

    @BeforeEach
    void setUp() {
        service = new ShoppingCartService();
        ReflectionTestUtils.setField(service, "shoppingCartRepository", shoppingCartRepository);
        ReflectionTestUtils.setField(service, "productoRepository", productoRepository);
        ReflectionTestUtils.setField(service, "userRepository", userRepository);
        ReflectionTestUtils.setField(service, "userInteractionService", userInteractionService);
        ReflectionTestUtils.setField(service, "aiModelService", aiModelService);
    }

    @Test
    void agregarUnidad_doesNotExceedMaxQty() {
        User user = TestEntities.userCliente(UUID.fromString(userId));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        Producto producto = activeProduct();
        when(productoRepository.findById(productId)).thenReturn(Optional.of(producto));

        ShoppingCart cart = cartWithItem(productId, 10);
        when(shoppingCartRepository.findById(userId)).thenReturn(Optional.of(cart));

        var response = service.agregarUnidad(userId, productId);

        assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(10);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).quantity()).isEqualTo(10);
    }

    @Test
    void decrementar_removesLineWhenQuantityIsOne() {
        User user = TestEntities.userCliente(UUID.fromString(userId));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        ShoppingCart cart = cartWithItem(productId, 1);
        when(shoppingCartRepository.findById(userId)).thenReturn(Optional.of(cart));

        var response = service.decrementar(userId, productId);

        assertThat(cart.getItems()).isEmpty();
        assertThat(response.items()).isEmpty();
        verify(shoppingCartRepository).save(cart);
    }

    @Test
    void agregarUnidad_throwsWhenProductDeleted() {
        User user = TestEntities.userCliente(UUID.fromString(userId));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        Producto deleted = activeProduct();
        deleted.setDeleted(true);
        when(productoRepository.findById(productId)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.agregarUnidad(userId, productId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Producto no disponible.");
    }

    private Producto activeProduct() {
        Producto p = new Producto();
        p.setId(productId);
        p.setName("Pizza");
        p.setPrice(25.0);
        p.setDeleted(false);
        return p;
    }

    private ShoppingCart cartWithItem(String pid, int qty) {
        ShoppingCart cart = new ShoppingCart();
        cart.setUserId(userId);
        cart.setItems(new ArrayList<>());
        cart.getItems().add(new CartItemMongo(pid, qty));
        return cart;
    }
}
