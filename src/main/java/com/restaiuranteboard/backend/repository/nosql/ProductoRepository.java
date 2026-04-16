package com.restaiuranteboard.backend.repository.nosql;

import com.restaiuranteboard.backend.model.nosql.Producto;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface ProductoRepository extends MongoRepository<Producto, String> {
    
    List<Producto> findByIsDeletedFalse();
    
    List<Producto> findByCategoryAndIsDeletedFalse(String category);
}
