package com.restaiuranteboard.backend.repository.sql;

import com.restaiuranteboard.backend.model.sql.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InventoryRepository extends JpaRepository<Inventory, Integer> {
    
    List<Inventory> findAllByIsDeletedFalse();
    
    boolean existsByNameAndIsDeletedFalse(String name);
}
