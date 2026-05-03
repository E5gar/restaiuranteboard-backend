package com.restaiuranteboard.backend.repository.sql;

import com.restaiuranteboard.backend.model.sql.InventoryMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Integer> {
    List<InventoryMovement> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);
}
