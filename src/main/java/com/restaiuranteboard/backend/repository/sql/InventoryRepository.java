package com.restaiuranteboard.backend.repository.sql;

import com.restaiuranteboard.backend.model.sql.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Integer> {

    List<Inventory> findAllByIsDeletedFalse();

    boolean existsByNameAndIsDeletedFalse(String name);

    boolean existsByNameIgnoreCaseAndIsDeletedFalse(String name);

    boolean existsByNameIgnoreCaseAndIsDeletedFalseAndIdNot(String name, Integer id);

    Optional<Inventory> findByIdAndIsDeletedFalse(Integer id);
}
