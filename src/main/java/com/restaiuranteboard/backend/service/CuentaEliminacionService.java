package com.restaiuranteboard.backend.service;

import com.restaiuranteboard.backend.model.sql.User;
import com.restaiuranteboard.backend.repository.nosql.ShoppingCartRepository;
import com.restaiuranteboard.backend.repository.sql.RestaurantOrderRepository;
import com.restaiuranteboard.backend.repository.sql.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
public class CuentaEliminacionService {

    private static final Set<String> ESTADOS_PEDIDO_ACTIVO = Set.of(
            "PENDIENTE_PAGO",
            "VALIDANDO_PAGO",
            "PAGO_VALIDADO",
            "EN_COCINA",
            "PREPARADO",
            "EN_CAMINO"
    );

    private final UserRepository userRepository;
    private final RestaurantOrderRepository restaurantOrderRepository;
    private final ShoppingCartRepository shoppingCartRepository;
    private final PasswordEncoder passwordEncoder;

    public CuentaEliminacionService(
            UserRepository userRepository,
            RestaurantOrderRepository restaurantOrderRepository,
            ShoppingCartRepository shoppingCartRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.restaurantOrderRepository = restaurantOrderRepository;
        this.shoppingCartRepository = shoppingCartRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void eliminarCuentaCliente(User user, String passwordPlano) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("No autorizado.");
        }
        if (user.isDeleted()) {
            throw new IllegalArgumentException("La cuenta ya fue eliminada.");
        }
        if (user.getRole() == null || !"CLIENTE".equalsIgnoreCase(user.getRole().getName())) {
            throw new IllegalArgumentException("Solo los clientes pueden eliminar su cuenta desde el perfil.");
        }
        if (passwordPlano == null || passwordPlano.isBlank()) {
            throw new IllegalArgumentException("Debes ingresar tu contraseña actual.");
        }
        if (user.getPassword() == null || !passwordEncoder.matches(passwordPlano, user.getPassword())) {
            throw new IllegalArgumentException("La contraseña ingresada no es correcta.");
        }
        if (restaurantOrderRepository.existsByClient_IdAndStatusIn(user.getId(), ESTADOS_PEDIDO_ACTIVO)) {
            throw new IllegalStateException(
                    "No puedes eliminar tu cuenta mientras tengas pedidos en curso. "
                            + "Espera a que sean entregados o cancelados antes de continuar."
            );
        }

        UUID id = user.getId();
        String idStr = id.toString();

        user.setDeleted(true);
        user.setEmail(idStr + "@eliminado");
        user.setDni("ELIMINADO_" + idStr);
        user.setPhone(null);
        user.setFullName("Usuario Eliminado");
        user.setAddress(null);
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        userRepository.save(user);

        shoppingCartRepository.deleteById(idStr);
    }
}
