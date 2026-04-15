package com.restaiuranteboard.backend.repository.sql;

import com.restaiuranteboard.backend.model.sql.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InventoryRepository extends JpaRepository<Inventory, Integer> {
    
    // Listar todos los insumos activos en el almacén
    List<Inventory> findAllByIsDeletedFalse();
    
    // Verificar si ya existe un ingrediente por nombre (para evitar duplicados)
    boolean existsByNameAndIsDeletedFalse(String name);
}