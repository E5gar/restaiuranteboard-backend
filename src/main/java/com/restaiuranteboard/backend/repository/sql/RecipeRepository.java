package com.restaiuranteboard.backend.repository.sql;

import com.restaiuranteboard.backend.model.sql.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RecipeRepository extends JpaRepository<Recipe, Integer> {
    
    List<Recipe> findByMongoProductId(String mongoProductId);
    
    List<Recipe> findByMongoProductIdAndIsDeletedFalse(String mongoProductId);
}
