package com.restaiuranteboard.backend.repository.sql;

import com.restaiuranteboard.backend.model.sql.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByEmail(String email);
    boolean existsByDni(String dni);
    Optional<User> findByEmail(String email);
    boolean existsByPhone(String phone);
}