package com.restaiuranteboard.backend.integration.service;

import com.restaiuranteboard.backend.integration.support.JpaIntegrationTestBase;
import com.restaiuranteboard.backend.model.sql.RestaurantOrder;
import com.restaiuranteboard.backend.model.sql.User;
import com.restaiuranteboard.backend.repository.nosql.ShoppingCartRepository;
import com.restaiuranteboard.backend.repository.sql.RestaurantOrderRepository;
import com.restaiuranteboard.backend.service.CuentaEliminacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@Import(CuentaEliminacionServiceIntegrationTest.Config.class)
class CuentaEliminacionServiceIntegrationTest extends JpaIntegrationTestBase {

    @Autowired
    private CuentaEliminacionService cuentaEliminacionService;

    @Autowired
    private RestaurantOrderRepository restaurantOrderRepository;

    @Autowired
    private ShoppingCartRepository shoppingCartRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User cliente;

    @BeforeEach
    void setUp() {
        restaurantOrderRepository.deleteAll();
        userRepository.deleteAll();
        seedRoles();
        cliente = persistCliente("eliminar@gmail.com", "99887766");
    }

    @Test
    void eliminarCuentaCliente_anonymizesUserAndKeepsOrderReference() {
        RestaurantOrder order = new RestaurantOrder();
        order.setClient(cliente);
        order.setStatus("ENTREGADO");
        order.setTotalPrice(new BigDecimal("30.00"));
        restaurantOrderRepository.save(order);

        cuentaEliminacionService.eliminarCuentaCliente(cliente, "Secret1@");

        User updated = userRepository.findById(cliente.getId()).orElseThrow();
        assertThat(updated.isDeleted()).isTrue();
        assertThat(updated.getEmail()).endsWith("@eliminado");
        assertThat(updated.getDni()).startsWith("ELIMINADO_");
        assertThat(updated.getFullName()).isEqualTo("Usuario Eliminado");
        assertThat(userRepository.existsByEmail("eliminar@gmail.com")).isFalse();
        assertThat(restaurantOrderRepository.findByClient_IdOrderByCreatedAtDesc(cliente.getId())).hasSize(1);
        verify(shoppingCartRepository).deleteById(cliente.getId().toString());
    }

    @Test
    void eliminarCuentaCliente_blocksWhenActiveOrdersExist() {
        RestaurantOrder active = new RestaurantOrder();
        active.setClient(cliente);
        active.setStatus("EN_CAMINO");
        active.setTotalPrice(new BigDecimal("15.00"));
        restaurantOrderRepository.save(active);

        assertThatThrownBy(() -> cuentaEliminacionService.eliminarCuentaCliente(cliente, "Secret1@"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pedidos en curso");
    }

    @TestConfiguration
    static class Config {
        @Bean
        @org.springframework.context.annotation.Primary
        ShoppingCartRepository shoppingCartRepositoryMock() {
            return mock(ShoppingCartRepository.class);
        }

        @Bean
        CuentaEliminacionService cuentaEliminacionService(
                com.restaiuranteboard.backend.repository.sql.UserRepository userRepository,
                RestaurantOrderRepository restaurantOrderRepository,
                ShoppingCartRepository shoppingCartRepositoryMock,
                PasswordEncoder passwordEncoder
        ) {
            return new CuentaEliminacionService(
                    userRepository,
                    restaurantOrderRepository,
                    shoppingCartRepositoryMock,
                    passwordEncoder
            );
        }
    }
}
