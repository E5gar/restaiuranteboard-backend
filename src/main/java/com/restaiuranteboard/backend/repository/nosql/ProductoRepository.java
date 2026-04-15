package com.restaiuranteboard.backend.repository.nosql;

import com.restaiuranteboard.backend.model.nosql.Producto;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface ProductoRepository extends MongoRepository<Producto, String> {
    
    // Recuperar solo productos que no han sido eliminados lógicamente
    List<Producto> findByIsDeletedFalse();
    
    // Buscar por categoría (útil para el menú del cliente)
    List<Producto> findByCategoryAndIsDeletedFalse(String category);
}