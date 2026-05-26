package com.restaiuranteboard.backend.service;

import com.restaiuranteboard.backend.dto.SeguimientoPedidoListasResponse;
import com.restaiuranteboard.backend.model.sql.RestaurantOrder;
import com.restaiuranteboard.backend.model.sql.User;
import com.restaiuranteboard.backend.repository.nosql.ProductoRepository;
import com.restaiuranteboard.backend.repository.sql.OrderItemRepository;
import com.restaiuranteboard.backend.repository.sql.RestaurantOrderRepository;
import com.restaiuranteboard.backend.repository.sql.UserRepository;
import com.restaiuranteboard.backend.support.TestEntities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeguimientoPedidoServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RestaurantOrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ProductoRepository productoRepository;

    private SeguimientoPedidoService service;

    private User client;

    @BeforeEach
    void setUp() {
        service = new SeguimientoPedidoService();
        ReflectionTestUtils.setField(service, "userRepository", userRepository);
        ReflectionTestUtils.setField(service, "orderRepository", orderRepository);
        ReflectionTestUtils.setField(service, "orderItemRepository", orderItemRepository);
        ReflectionTestUtils.setField(service, "productoRepository", productoRepository);
        client = TestEntities.userCliente();
    }

    @Test
    void listar_throwsWhenUserNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listar(userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Usuario no encontrado.");
    }

    @Test
    void listar_returnsPendientesAndFinalizados() {
        RestaurantOrder pendiente = new RestaurantOrder();
        pendiente.setId(UUID.randomUUID());
        pendiente.setStatus("VALIDANDO_PAGO");
        pendiente.setCreatedAt(LocalDateTime.now());
        pendiente.setTotalPrice(new BigDecimal("30.00"));

        when(userRepository.findById(client.getId())).thenReturn(Optional.of(client));
        when(orderRepository.loadSeguimientoPendientes(eq(client.getId()), any())).thenReturn(List.of(pendiente));
        when(orderRepository.loadSeguimientoFinalizados(eq(client.getId()), any())).thenReturn(List.of());
        when(orderItemRepository.findByRestaurantOrder_IdIn(any())).thenReturn(List.of());

        SeguimientoPedidoListasResponse result = service.listar(client.getId());

        assertThat(result.pendientes()).hasSize(1);
        assertThat(result.pendientes().get(0).estado()).isEqualTo("VALIDANDO_PAGO");
        assertThat(result.finalizados()).isEmpty();
    }

    @Test
    void obtenerPedidoActual_throwsWhenNoOrders() {
        when(userRepository.findById(client.getId())).thenReturn(Optional.of(client));
        when(orderRepository.loadSeguimientoPendientes(eq(client.getId()), any())).thenReturn(List.of());
        when(orderRepository.loadSeguimientoFinalizados(eq(client.getId()), any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.obtenerPedidoActual(client.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No tienes pedidos registrados.");
    }
}
