package com.restaiuranteboard.backend.repository.sql;

import com.restaiuranteboard.backend.model.sql.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RecipeRepository extends JpaRepository<Recipe, Integer> {
    
    // Buscar todos los ingredientes que componen un plato de MongoDB
    List<Recipe> findByMongoProductId(String mongoProductId);
    
    // Buscar recetas activas por el ID de producto de Mongo
    List<Recipe> findByMongoProductIdAndIsDeletedFalse(String mongoProductId);
}