package com.restaiuranteboard.backend.integration.repository;

import com.restaiuranteboard.backend.integration.support.JpaIntegrationTestBase;
import com.restaiuranteboard.backend.model.sql.RestaurantOrder;
import com.restaiuranteboard.backend.model.sql.User;
import com.restaiuranteboard.backend.repository.sql.RestaurantOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RestaurantOrderRepositoryIntegrationTest extends JpaIntegrationTestBase {

    @Autowired
    private RestaurantOrderRepository restaurantOrderRepository;

    private User cliente;

    @BeforeEach
    void setUp() {
        restaurantOrderRepository.deleteAll();
        userRepository.deleteAll();
        seedRoles();
        cliente = persistCliente("pedidos@gmail.com", "44556677");
    }

    @Test
    void existsByClient_IdAndStatusIn_detectsActiveOrders() {
        RestaurantOrder active = new RestaurantOrder();
        active.setClient(cliente);
        active.setStatus("EN_COCINA");
        active.setTotalPrice(new BigDecimal("45.50"));
        restaurantOrderRepository.save(active);

        RestaurantOrder done = new RestaurantOrder();
        done.setClient(cliente);
        done.setStatus("ENTREGADO");
        done.setTotalPrice(new BigDecimal("20.00"));
        restaurantOrderRepository.save(done);

        boolean hasActive = restaurantOrderRepository.existsByClient_IdAndStatusIn(
                cliente.getId(),
                Set.of("PENDIENTE_PAGO", "VALIDANDO_PAGO", "PAGO_VALIDADO", "EN_COCINA", "PREPARADO", "EN_CAMINO")
        );

        assertThat(hasActive).isTrue();
    }

    @Test
    void findByClient_IdOrderByCreatedAtDesc_returnsOrdersForClient() {
        RestaurantOrder o1 = new RestaurantOrder();
        o1.setClient(cliente);
        o1.setStatus("ENTREGADO");
        o1.setTotalPrice(new BigDecimal("10.00"));
        restaurantOrderRepository.save(o1);

        assertThat(restaurantOrderRepository.findByClient_IdOrderByCreatedAtDesc(cliente.getId()))
                .hasSize(1)
                .first()
                .extracting(RestaurantOrder::getStatus)
                .isEqualTo("ENTREGADO");
    }
}
