package com.restaiuranteboard.backend.repository.sql;

import com.restaiuranteboard.backend.model.sql.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByEmail(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByDni(String dni);
    Optional<User> findByEmail(String email);
    boolean existsByPhone(String phone);
    boolean existsByPhoneAndIdNot(String phone, UUID id);
    List<User> findByRole_NameAndIsDeletedFalse(String roleName);

    long countByRole_NameAndIsDeletedFalseAndCreatedAtBetween(String roleName, LocalDateTime from, LocalDateTime toExclusive);
}
