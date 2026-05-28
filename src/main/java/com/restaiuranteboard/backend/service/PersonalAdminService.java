package com.restaiuranteboard.backend.service;

import com.restaiuranteboard.backend.model.sql.User;
import com.restaiuranteboard.backend.repository.sql.RestaurantOrderRepository;
import com.restaiuranteboard.backend.repository.sql.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class PersonalAdminService {

    public record PersonalActivoDto(String userId, String fullName, String role, String email) {
    }

    private static final Set<String> ROLES_EMPLEADOS_ACTIVOS = Set.of("CAJERO", "COCINERO", "REPARTIDOR");

    private static final Set<String> ESTADOS_REPARTIDOR_ACTIVO = Set.of(
            "PREPARADO",
            "EN_CAMINO"
    );

    private final UserRepository userRepository;
    private final RestaurantOrderRepository restaurantOrderRepository;
    private final PasswordEncoder passwordEncoder;

    public PersonalAdminService(
            UserRepository userRepository,
            RestaurantOrderRepository restaurantOrderRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.restaurantOrderRepository = restaurantOrderRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<PersonalActivoDto> listarPersonalActivo() {
        List<PersonalActivoDto> out = new ArrayList<>();
        for (String role : ROLES_EMPLEADOS_ACTIVOS) {
            List<User> users = userRepository.findByRole_NameAndIsDeletedFalse(role);
            for (User u : users) {
                out.add(new PersonalActivoDto(
                        u.getId().toString(),
                        u.getFullName(),
                        role,
                        u.getEmail()
                ));
            }
        }
        return out;
    }

    @Transactional
    public void eliminarEmpleado(UUID empleadoId) {
        if (empleadoId == null) {
            throw new IllegalArgumentException("Empleado requerido.");
        }
        User empleado = userRepository.findById(empleadoId).orElse(null);
        if (empleado == null || empleado.isDeleted()) {
            throw new IllegalArgumentException("Empleado no encontrado.");
        }
        if (empleado.getRole() == null || !ROLES_EMPLEADOS_ACTIVOS.contains(empleado.getRole().getName())) {
            throw new IllegalArgumentException("Solo se puede dar de baja a personal de roles válidos.");
        }

        String role = empleado.getRole().getName();
        if ("REPARTIDOR".equals(role)) {
            boolean tienePedidosActivos = restaurantOrderRepository.existsByDeliveryPerson_IdAndStatusIn(
                    empleado.getId(),
                    ESTADOS_REPARTIDOR_ACTIVO
            );
            if (tienePedidosActivos) {
                throw new IllegalStateException("No puedes eliminar este repartidor mientras tenga entregas en curso. Debes esperar a que finalicen.");
            }
        }

        softDeleteAnonymize(empleado);
    }

    private void softDeleteAnonymize(User empleado) {
        UUID id = empleado.getId();
        String idStr = id.toString();

        empleado.setDeleted(true);
        empleado.setEmail(idStr + "@eliminado-personal");
        empleado.setDni("ELIM_PERS_" + idStr);
        empleado.setPhone("ELIM_PERS_" + idStr);

        String original = empleado.getFullName() == null ? "" : empleado.getFullName().trim();
        if (original.isBlank()) original = "Empleado";
        if (!original.startsWith("[Inactivo]")) {
            empleado.setFullName("[Inactivo] " + original);
        } else {
            empleado.setFullName(original);
        }

        empleado.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        empleado.setGoogleSub(null);
        empleado.setAuthProvider("LOCAL");
        userRepository.save(empleado);
    }
}

