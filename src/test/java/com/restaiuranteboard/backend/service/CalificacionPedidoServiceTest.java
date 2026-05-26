package com.restaiuranteboard.backend.service;

import com.restaiuranteboard.backend.model.sql.OrderRating;
import com.restaiuranteboard.backend.model.sql.RestaurantOrder;
import com.restaiuranteboard.backend.model.sql.User;
import com.restaiuranteboard.backend.repository.sql.OrderRatingRepository;
import com.restaiuranteboard.backend.repository.sql.RestaurantOrderRepository;
import com.restaiuranteboard.backend.repository.sql.UserRepository;
import com.restaiuranteboard.backend.support.TestEntities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalificacionPedidoServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RestaurantOrderRepository orderRepository;

    @Mock
    private OrderRatingRepository orderRatingRepository;

    private CalificacionPedidoService service;

    private User client;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        service = new CalificacionPedidoService();
        ReflectionTestUtils.setField(service, "userRepository", userRepository);
        ReflectionTestUtils.setField(service, "orderRepository", orderRepository);
        ReflectionTestUtils.setField(service, "orderRatingRepository", orderRatingRepository);
        client = TestEntities.userCliente();
        orderId = UUID.randomUUID();
    }

    @Test
    void calificar_throwsWhenStarsOutOfRange() {
        when(userRepository.findById(client.getId())).thenReturn(Optional.of(client));

        assertThatThrownBy(() -> service.calificar(client.getId(), orderId, 0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Debes calificar con entre 1 y 5 estrellas.");

        assertThatThrownBy(() -> service.calificar(client.getId(), orderId, 6, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Debes calificar con entre 1 y 5 estrellas.");

        verify(orderRepository, never()).findById(any());
    }

    @Test
    void calificar_throwsWhenOrderNotDelivered() {
        when(userRepository.findById(client.getId())).thenReturn(Optional.of(client));
        RestaurantOrder order = orderForClient("EN_CAMINO");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.calificar(client.getId(), orderId, 5, "Bueno"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Solo puedes calificar pedidos entregados.");

        verify(orderRatingRepository, never()).save(any());
    }

    @Test
    void calificar_savesRatingWhenOrderDelivered() {
        when(userRepository.findById(client.getId())).thenReturn(Optional.of(client));
        RestaurantOrder order = orderForClient("ENTREGADO");
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRatingRepository.existsByOrder_Id(orderId)).thenReturn(false);

        service.calificar(client.getId(), orderId, 4, "Excelente");

        ArgumentCaptor<OrderRating> ratingCaptor = ArgumentCaptor.forClass(OrderRating.class);
        verify(orderRatingRepository).save(ratingCaptor.capture());
        assertThat(ratingCaptor.getValue().getStars()).isEqualTo(4);
        assertThat(ratingCaptor.getValue().getComment()).isEqualTo("Excelente");
        assertThat(order.getIsRated()).isTrue();
        verify(orderRepository).save(order);
    }

    private RestaurantOrder orderForClient(String status) {
        RestaurantOrder order = new RestaurantOrder();
        order.setId(orderId);
        order.setClient(client);
        order.setStatus(status);
        order.setIsRated(false);
        return order;
    }
}
