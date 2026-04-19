package com.restaiuranteboard.backend.repository.sql;

import com.restaiuranteboard.backend.model.sql.InventoryMovement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Integer> {
}
