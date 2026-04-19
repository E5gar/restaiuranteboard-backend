package com.restaiuranteboard.backend.controller;

import com.restaiuranteboard.backend.dto.ProductoRequest;
import com.restaiuranteboard.backend.model.nosql.Producto;
import com.restaiuranteboard.backend.model.sql.Inventory;
import com.restaiuranteboard.backend.model.sql.InventoryMovement;
import com.restaiuranteboard.backend.model.sql.Recipe;
import com.restaiuranteboard.backend.repository.nosql.ProductoRepository;
import com.restaiuranteboard.backend.repository.sql.InventoryMovementRepository;
import com.restaiuranteboard.backend.repository.sql.InventoryRepository;
import com.restaiuranteboard.backend.repository.sql.RecipeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/catalogo")
public class CatalogoController {

    private static final Set<String> CATEGORIAS_PRODUCTO = Set.of(
            "Entrada", "Plato Principal", "Postres", "Bebidas"
    );

    private static final Set<String> CATEGORIAS_INGREDIENTE = Set.of(
            "Verduras", "Carnes", "Huevos", "Marinos", "Abarrotes", "Lácteos", "Bebidas", "Frutas", "Panadería"
    );

    private static final Set<String> UNIDADES_INGREDIENTE = Set.of("UNIDADES", "GR", "ML");

    @Autowired private ProductoRepository productoMongoRepo;
    @Autowired private InventoryRepository inventorySqlRepo;
    @Autowired private RecipeRepository recipeSqlRepo;
    @Autowired private InventoryMovementRepository inventoryMovementRepo;

    @GetMapping("/ingredientes")
    public List<Inventory> listarIngredientes() {
        return inventorySqlRepo.findAllByIsDeletedFalse();
    }

    /**
     * Aumenta stock del insumo y registra movimiento ABASTECIMIENTO.
     * Body JSON: quantity (obligatorio), unitCost (opcional), reason (opcional).
     */
    @PostMapping("/ingredientes/{id}/abastecer")
    @Transactional
    public ResponseEntity<?> abastecerInsumo(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        Inventory inv = inventorySqlRepo.findById(id).orElse(null);
        if (inv == null || inv.isDeleted()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Insumo no encontrado."));
        }

        Object qObj = body.get("quantity");
        if (qObj == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "La cantidad de abastecimiento es obligatoria."));
        }
        Double qty = toPositiveDouble(qObj);
        if (qty == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Cantidad inválida. No uses notación científica (e)."));
        }

        ResponseEntity<?> qtyErr = validarCantidadAbastecimiento(qty, inv.getUnit());
        if (qtyErr != null) return qtyErr;

        Double unitCost = null;
        Object uc = body.get("unitCost");
        if (uc != null && !(uc instanceof String s && s.trim().isEmpty())) {
            Double parsed = toNonNegativeDouble(uc);
            if (parsed == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Costo unitario de compra inválido."));
            }
            ResponseEntity<?> costErr = validarNoNegativoMax2Dec(parsed, "El costo unitario de compra");
            if (costErr != null) return costErr;
            unitCost = parsed;
        }

        String reason = trimToNull(body.get("reason") == null ? null : String.valueOf(body.get("reason")));

        double prev = inv.getStockQuantity() != null ? inv.getStockQuantity() : 0.0;
        double newStock = prev + qty;

        inv.setStockQuantity(newStock);
        inventorySqlRepo.save(inv);

        InventoryMovement m = new InventoryMovement();
        m.setInventoryId(id);
        m.setQuantity(scale2(qty));
        m.setPreviousStock(scale2(prev));
        m.setNewStock(scale2(newStock));
        m.setUnitCost(unitCost != null ? scale2(unitCost) : null);
        m.setMovementType("ABASTECIMIENTO");
        m.setReason(reason);
        m.setCreatedAt(LocalDateTime.now());
        inventoryMovementRepo.save(m);

        return ResponseEntity.ok(Map.of(
                "message", "Abastecimiento registrado.",
                "newStock", newStock
        ));
    }

    @PostMapping("/ingredientes")
    public ResponseEntity<?> guardarIngrediente(@RequestBody Inventory item) {
        ResponseEntity<?> err = validarInventario(item);
        if (err != null) return err;

        String nombre = item.getName().trim();
        if (inventorySqlRepo.existsByNameIgnoreCaseAndIsDeletedFalse(nombre)) {
            return ResponseEntity.badRequest().body(Map.of("message", "El insumo ya existe"));
        }

        item.setName(nombre);
        item.setDeleted(false);
        inventorySqlRepo.save(item);
        return ResponseEntity.ok(Map.of("message", "Ingrediente guardado en SQL"));
    }

    @GetMapping("/productos")
    public List<Producto> listarProductos() {
        return productoMongoRepo.findByIsDeletedFalse();
    }

    @PostMapping("/productos")
    public ResponseEntity<?> guardarProducto(@RequestBody ProductoRequest request) {
        Producto guardado = null;
        try {
            ResponseEntity<?> err = validarProductoRequest(request);
            if (err != null) return err;

            Producto p = request.getProducto();
            p.setActive(true);
            p.setDeleted(false);

            String nombreProducto = p.getName().trim();
            if (productoMongoRepo.existsByNameIgnoreCaseAndIsDeletedFalse(nombreProducto)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Esa receta ya existe"));
            }
            p.setName(nombreProducto);

            guardado = productoMongoRepo.save(p);
            String mongoId = guardado.getId();

            for (ProductoRequest.RecetaItemDTO item : request.getReceta()) {
                Inventory ing = inventorySqlRepo.findById(item.getIngredientId())
                        .orElseThrow(() -> new IllegalArgumentException("Insumo no encontrado."));
                if (ing.isDeleted()) {
                    throw new IllegalArgumentException("Insumo no disponible.");
                }
                ResponseEntity<?> qErr = validarCantidadReceta(item.getQuantity(), ing.getUnit());
                if (qErr != null) {
                    productoMongoRepo.deleteById(mongoId);
                    return qErr;
                }

                Recipe r = new Recipe();
                r.setMongoProductId(mongoId);
                r.setIngredient(ing);
                r.setQuantityToSubtract(item.getQuantity());
                r.setDeleted(false);
                recipeSqlRepo.save(r);
            }

            return ResponseEntity.ok(Map.of("message", "Producto y Receta creados exitosamente"));
        } catch (IllegalArgumentException e) {
            if (guardado != null && guardado.getId() != null) {
                try {
                    productoMongoRepo.deleteById(guardado.getId());
                } catch (Exception ignored) {
                    /* silencioso */
                }
            }
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            if (guardado != null && guardado.getId() != null) {
                try {
                    productoMongoRepo.deleteById(guardado.getId());
                } catch (Exception ignored) {
                    /* silencioso */
                }
            }
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

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private ResponseEntity<?> validarInventario(Inventory item) {
        if (item == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Cuerpo inválido."));
        }
        String nombre = trimToNull(item.getName());
        if (nombre == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "El nombre del insumo es obligatorio."));
        }
        item.setName(nombre);

        String cat = item.getCategory();
        if (cat == null || !CATEGORIAS_INGREDIENTE.contains(cat.trim())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Categoría de insumo no válida."));
        }
        item.setCategory(cat.trim());

        String unit = item.getUnit();
        if (unit == null || !UNIDADES_INGREDIENTE.contains(unit.trim())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Unidad de medida no válida."));
        }
        item.setUnit(unit.trim());

        if (item.getStockQuantity() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "El stock inicial es obligatorio."));
        }
        ResponseEntity<?> stockErr = validarStockOCosto(item.getStockQuantity(), item.getUnit(), true);
        if (stockErr != null) return stockErr;

        if (item.getPrice() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "El costo unitario es obligatorio."));
        }
        ResponseEntity<?> costErr = validarNoNegativoMax2Dec(item.getPrice(), "El costo unitario");
        if (costErr != null) return costErr;

        return null;
    }

    /** stock: si allowIntegerOnly (UNIDADES) → sin decimales; GR/ML → máx. 2 decimales. */
    private ResponseEntity<?> validarStockOCosto(Double value, String unit, boolean esStock) {
        ResponseEntity<?> base = validarNoNegativoMax2Dec(value, esStock ? "El stock inicial" : "El valor");
        if (base != null) return base;
        if ("UNIDADES".equalsIgnoreCase(unit)) {
            if (!esEntero(value)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message",
                        esStock ? "Con unidad Unidades el stock no puede tener decimales." : "Valor inválido."));
            }
        } else {
            if (!maxDosDecimales(value)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "message",
                        esStock ? "Con gramos o mililitros el stock admite como máximo dos decimales." : "Máximo dos decimales."));
            }
        }
        return null;
    }

    private ResponseEntity<?> validarCantidadReceta(Double quantity, String unitIngrediente) {
        if (quantity == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "La cantidad en la receta es obligatoria."));
        }
        if (quantity < 0 || Double.isNaN(quantity) || Double.isInfinite(quantity)) {
            return ResponseEntity.badRequest().body(Map.of("message", "La cantidad en la receta no puede ser negativa."));
        }
        return validarStockOCosto(quantity, unitIngrediente, true);
    }

    /** Cantidad a sumar al stock: estrictamente &gt; 0; mismas reglas de decimales que stock según unidad. */
    private ResponseEntity<?> validarCantidadAbastecimiento(Double quantity, String unitIngrediente) {
        if (quantity == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "La cantidad es obligatoria."));
        }
        if (quantity <= 0 || Double.isNaN(quantity) || Double.isInfinite(quantity)) {
            return ResponseEntity.badRequest().body(Map.of("message", "La cantidad de abastecimiento debe ser mayor que cero."));
        }
        return validarStockOCosto(quantity, unitIngrediente, true);
    }

    private static BigDecimal scale2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Número positivo desde JSON (Number o String). Rechaza e/E en texto.
     */
    private static Double toPositiveDouble(Object o) {
        Double d = toDoubleLoose(o);
        if (d == null || d <= 0) return null;
        return d;
    }

    private static Double toNonNegativeDouble(Object o) {
        Double d = toDoubleLoose(o);
        if (d == null || d < 0) return null;
        return d;
    }

    private static Double toDoubleLoose(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) {
            double d = n.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) return null;
            return d;
        }
        if (o instanceof String s) {
            String t = s.trim().replace(',', '.');
            if (t.isEmpty()) return null;
            if (t.contains("e") || t.contains("E")) return null;
            try {
                return Double.parseDouble(t);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private ResponseEntity<?> validarNoNegativoMax2Dec(Double value, String campo) {
        if (value < 0 || Double.isNaN(value) || Double.isInfinite(value)) {
            return ResponseEntity.badRequest().body(Map.of("message", campo + " no puede ser negativo."));
        }
        if (!maxDosDecimales(value)) {
            return ResponseEntity.badRequest().body(Map.of("message", campo + " admite como máximo dos decimales."));
        }
        return null;
    }

    private static boolean maxDosDecimales(double v) {
        BigDecimal redondeado = BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(v).subtract(redondeado).abs().compareTo(new BigDecimal("0.0000001")) <= 0;
    }

    private static boolean esEntero(double v) {
        return Math.abs(v - Math.rint(v)) < 1e-9;
    }

    private ResponseEntity<?> validarPrecioVenta(Double price) {
        if (price == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "El precio de venta es obligatorio."));
        }
        if (price < 0.10 - 1e-9 || Double.isNaN(price) || Double.isInfinite(price)) {
            return ResponseEntity.badRequest().body(Map.of("message", "El precio de venta mínimo es 0.10."));
        }
        return validarNoNegativoMax2Dec(price, "El precio de venta");
    }

    private ResponseEntity<?> validarProductoRequest(ProductoRequest request) {
        if (request == null || request.getProducto() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Solicitud inválida."));
        }
        Producto p = request.getProducto();
        String nombre = trimToNull(p.getName());
        if (nombre == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "El nombre del producto es obligatorio."));
        }
        p.setName(nombre);

        String cat = p.getCategory();
        if (cat == null || !CATEGORIAS_PRODUCTO.contains(cat.trim())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Categoría de producto no válida."));
        }
        p.setCategory(cat.trim());

        ResponseEntity<?> precioErr = validarPrecioVenta(p.getPrice());
        if (precioErr != null) return precioErr;

        if (p.getImagesBase64() == null || p.getImagesBase64().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Debe incluir al menos una imagen del producto."));
        }

        if (request.getReceta() == null || request.getReceta().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "La receta debe incluir al menos un insumo."));
        }

        for (ProductoRequest.RecetaItemDTO item : request.getReceta()) {
            if (item.getIngredientId() == null || item.getQuantity() == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Cada línea de receta requiere insumo y cantidad."));
            }
            Inventory ing = inventorySqlRepo.findById(item.getIngredientId()).orElse(null);
            if (ing == null || ing.isDeleted()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Insumo inválido en la receta."));
            }
            ResponseEntity<?> qErr = validarCantidadReceta(item.getQuantity(), ing.getUnit());
            if (qErr != null) return qErr;
        }

        return null;
    }
}
