package com.restaiuranteboard.backend.controller;

import com.restaiuranteboard.backend.dto.ProductoRequest;
import com.restaiuranteboard.backend.model.nosql.Producto;
import com.restaiuranteboard.backend.model.sql.Inventory;
import com.restaiuranteboard.backend.model.sql.Recipe;
import com.restaiuranteboard.backend.repository.nosql.ProductoRepository;
import com.restaiuranteboard.backend.repository.sql.InventoryRepository;
import com.restaiuranteboard.backend.repository.sql.RecipeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/catalogo")
public class CatalogoController {

    @Autowired private ProductoRepository productoMongoRepo;
    @Autowired private InventoryRepository inventorySqlRepo;
    @Autowired private RecipeRepository recipeSqlRepo;

    @GetMapping("/ingredientes")
    public List<Inventory> listarIngredientes() {
        return inventorySqlRepo.findAllByIsDeletedFalse();
    }

    @PostMapping("/ingredientes")
    public ResponseEntity<?> guardarIngrediente(@RequestBody Inventory item) {
        inventorySqlRepo.save(item);
        return ResponseEntity.ok(Map.of("message", "Ingrediente guardado en SQL"));
    }

    @GetMapping("/productos")
    public List<Producto> listarProductos() {
        return productoMongoRepo.findByIsDeletedFalse();
    }

    @PostMapping("/productos")
    public ResponseEntity<?> guardarProducto(@RequestBody ProductoRequest request) {
        try {
            Producto nuevoProducto = productoMongoRepo.save(request.getProducto());
            String mongoId = nuevoProducto.getId();

            for (ProductoRequest.RecetaItemDTO item : request.getReceta()) {
                Recipe r = new Recipe();
                r.setMongoProductId(mongoId);
                r.setIngredient(inventorySqlRepo.findById(item.getIngredientId()).orElseThrow());
                r.setQuantityToSubtract(item.getQuantity());
                recipeSqlRepo.save(r);
            }

            return ResponseEntity.ok(Map.of("message", "Producto y Receta creados exitosamente"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Error: " + e.getMessage()));
        }
    }

    @DeleteMapping("/productos/{id}")
    public ResponseEntity<?> eliminarProducto(@PathVariable String id) {
        Producto p = productoMongoRepo.findById(id).orElseThrow();
        p.setDeleted(true);
        productoMongoRepo.save(p);

        List<Recipe> recetas = recipeSqlRepo.findByMongoProductId(id);
        recetas.forEach(r -> r.setDeleted(true));
        recipeSqlRepo.saveAll(recetas);

        return ResponseEntity.ok(Map.of("message", "Producto eliminado lógicamente"));
    }
}
