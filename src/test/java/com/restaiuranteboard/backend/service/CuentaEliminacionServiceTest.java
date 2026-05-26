package com.restaiuranteboard.backend.service;

import com.restaiuranteboard.backend.model.sql.User;
import com.restaiuranteboard.backend.repository.nosql.ShoppingCartRepository;
import com.restaiuranteboard.backend.repository.sql.RestaurantOrderRepository;
import com.restaiuranteboard.backend.repository.sql.UserRepository;
import com.restaiuranteboard.backend.support.TestEntities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CuentaEliminacionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RestaurantOrderRepository restaurantOrderRepository;

    @Mock
    private ShoppingCartRepository shoppingCartRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private CuentaEliminacionService service;

    @Test
    void eliminarCuentaCliente_throwsWhenPasswordIncorrect() {
        User user = TestEntities.userCliente();
        when(passwordEncoder.matches("wrong", user.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> service.eliminarCuentaCliente(user, "wrong"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La contraseña ingresada no es correcta.");

        verify(userRepository, never()).save(any());
    }

    @Test
    void eliminarCuentaCliente_throwsWhenActiveOrdersExist() {
        User user = TestEntities.userCliente();
        when(passwordEncoder.matches("correct", user.getPassword())).thenReturn(true);
        when(restaurantOrderRepository.existsByClient_IdAndStatusIn(eq(user.getId()), any(Set.class)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.eliminarCuentaCliente(user, "correct"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pedidos en curso");

        verify(userRepository, never()).save(any());
    }

    @Test
    void eliminarCuentaCliente_anonymizesUserAndDeletesCart() {
        UUID id = UUID.randomUUID();
        User user = TestEntities.userCliente(id);
        when(passwordEncoder.matches("correct", user.getPassword())).thenReturn(true);
        when(restaurantOrderRepository.existsByClient_IdAndStatusIn(eq(id), any(Set.class))).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("new-encoded");

        service.eliminarCuentaCliente(user, "correct");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.isDeleted()).isTrue();
        assertThat(saved.getEmail()).isEqualTo(id + "@eliminado");
        assertThat(saved.getDni()).isEqualTo("ELIMINADO_" + id);
        assertThat(saved.getFullName()).isEqualTo("Usuario Eliminado");
        assertThat(saved.getPhone()).isNull();
        assertThat(saved.getAddress()).isNull();
        verify(shoppingCartRepository).deleteById(id.toString());
    }
}
