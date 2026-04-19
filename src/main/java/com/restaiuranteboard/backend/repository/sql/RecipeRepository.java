package com.restaiuranteboard.backend.repository.sql;

import com.restaiuranteboard.backend.model.sql.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RecipeRepository extends JpaRepository<Recipe, Integer> {

    List<Recipe> findByMongoProductId(String mongoProductId);

    List<Recipe> findByMongoProductIdAndIsDeletedFalse(String mongoProductId);

    @Query("SELECT DISTINCT r.mongoProductId FROM Recipe r WHERE r.ingredient.id = :invId AND r.isDeleted = false")
    List<String> findDistinctActiveMongoProductIdsByIngredientId(@Param("invId") Integer inventoryId);
}
