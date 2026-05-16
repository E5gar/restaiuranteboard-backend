package com.restaiuranteboard.backend.service;

import com.restaiuranteboard.backend.export.DatasetCsvWriter;
import com.restaiuranteboard.backend.model.nosql.Producto;
import com.restaiuranteboard.backend.model.nosql.UserInteraction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class AiTrainingDatasetExportService {

    private static final String SQL_COSTOS_PRODUCTOS = """
            SELECT r.mongo_product_id AS product_id,
                   SUM(r.quantity_to_subtract * i.price) AS cost_to_make
            FROM recipes r
            JOIN inventory i ON r.ingredient_id = i.id
            WHERE r.is_deleted = false AND i.is_deleted = false
            GROUP BY r.mongo_product_id
            """;

    private static final String SQL_VENTAS_HISTORIAL = """
            SELECT o.id AS order_id,
                   o.client_id,
                   oi.mongo_product_id AS product_id,
                   oi.quantity,
                   oi.price_at_moment AS sold_price,
                   o.status,
                   o.created_at,
                   o.weather_temp_c,
                   o.weather_condition,
                   o.moment_of_day,
                   o.day_of_week,
                   COALESCE(r.stars, 0) AS rating
            FROM orders o
            JOIN order_items oi ON o.id = oi.order_id
            LEFT JOIN order_ratings r ON o.id = r.order_id
            WHERE o.status NOT IN ('PENDIENTE_PAGO', 'VALIDANDO_PAGO')
            """;

    private static final String SQL_TRANSACCIONES_CARRITO = """
            SELECT o.id AS order_id,
                   oi.mongo_product_id AS product_id
            FROM orders o
            JOIN order_items oi ON o.id = oi.order_id
            WHERE o.status IN ('ENTREGADO', 'PAGO_VALIDADO', 'EN_COCINA', 'EN_CAMINO', 'PREPARADO')
            """;

    private static final String SQL_MOVIMIENTOS_INVENTARIO = """
            SELECT im.id AS movement_id,
                   im.inventory_id,
                   i.name AS ingredient_name,
                   i.category AS ingredient_category,
                   i.unit AS unit,
                   im.quantity,
                   im.previous_stock,
                   im.new_stock,
                   im.unit_cost,
                   im.movement_type,
                   im.reason,
                   DATE(im.created_at) AS movement_date,
                   EXTRACT(DOW FROM im.created_at) AS day_of_week,
                   EXTRACT(HOUR FROM im.created_at) AS hour_of_day,
                   TO_CHAR(im.created_at, 'YYYY-MM') AS year_month,
                   EXTRACT(WEEK FROM im.created_at) AS week_of_year,
                   im.created_at AS created_at
            FROM inventory_movements im
            JOIN inventory i ON im.inventory_id = i.id
            WHERE i.is_deleted = false
            ORDER BY im.inventory_id, im.created_at ASC
            """;

    private static final String SQL_CONSUMOS_DIARIOS = """
            SELECT im.inventory_id,
                   i.name AS ingredient_name,
                   i.category AS ingredient_category,
                   i.unit AS unit,
                   DATE(im.created_at) AS consumption_date,
                   EXTRACT(DOW FROM im.created_at) AS day_of_week,
                   EXTRACT(WEEK FROM im.created_at) AS week_of_year,
                   EXTRACT(MONTH FROM im.created_at) AS month,
                   EXTRACT(YEAR FROM im.created_at) AS year,
                   SUM(ABS(im.quantity)) AS total_consumed,
                   COUNT(*) AS num_consumption_events,
                   AVG(ABS(im.quantity)) AS avg_consumption_per_event,
                   MIN(im.new_stock) AS min_stock_reached,
                   MAX(im.previous_stock) AS max_stock_before
            FROM inventory_movements im
            JOIN inventory i ON im.inventory_id = i.id
            WHERE im.movement_type = 'SALIDA'
              AND i.is_deleted = false
            GROUP BY im.inventory_id, i.name, i.category, i.unit,
                     DATE(im.created_at),
                     EXTRACT(DOW FROM im.created_at),
                     EXTRACT(WEEK FROM im.created_at),
                     EXTRACT(MONTH FROM im.created_at),
                     EXTRACT(YEAR FROM im.created_at)
            ORDER BY im.inventory_id, consumption_date ASC
            """;

    private static final String SQL_HISTORIAL_STOCK_DIARIO = """
            WITH stock_snapshots AS (
                SELECT im.inventory_id,
                       DATE(im.created_at) AS snapshot_date,
                       LAST_VALUE(im.new_stock) OVER (
                           PARTITION BY im.inventory_id, DATE(im.created_at)
                           ORDER BY im.created_at
                           ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING
                       ) AS closing_stock,
                       FIRST_VALUE(im.previous_stock) OVER (
                           PARTITION BY im.inventory_id, DATE(im.created_at)
                           ORDER BY im.created_at
                           ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING
                       ) AS opening_stock,
                       ROW_NUMBER() OVER (
                           PARTITION BY im.inventory_id, DATE(im.created_at)
                           ORDER BY im.created_at DESC
                       ) AS rn
                FROM inventory_movements im
                JOIN inventory i ON im.inventory_id = i.id
                WHERE i.is_deleted = false
            )
            SELECT ss.inventory_id,
                   i.name AS ingredient_name,
                   i.unit AS unit,
                   i.category AS category,
                   ss.snapshot_date,
                   ss.opening_stock,
                   ss.closing_stock,
                   (ss.opening_stock - ss.closing_stock) AS net_consumed_day
            FROM stock_snapshots ss
            JOIN inventory i ON ss.inventory_id = i.id
            WHERE ss.rn = 1
            ORDER BY ss.inventory_id, ss.snapshot_date
            """;

    private static final String SQL_REPOSICIONES = """
            SELECT im.inventory_id,
                   i.name AS ingredient_name,
                   i.unit AS unit,
                   DATE(im.created_at) AS restock_date,
                   im.quantity AS restock_quantity,
                   im.unit_cost AS unit_cost,
                   im.previous_stock,
                   im.new_stock,
                   im.reason,
                   EXTRACT(DOW FROM im.created_at) AS day_of_week,
                   EXTRACT(MONTH FROM im.created_at) AS month,
                   EXTRACT(YEAR FROM im.created_at) AS year
            FROM inventory_movements im
            JOIN inventory i ON im.inventory_id = i.id
            WHERE im.movement_type = 'ABASTECIMIENTO'
              AND i.is_deleted = false
            ORDER BY im.inventory_id, im.created_at
            """;

    private static final String SQL_INVENTARIO_ACTUAL = """
            SELECT i.id AS inventory_id,
                   i.name AS ingredient_name,
                   i.category,
                   i.unit,
                   i.stock_quantity AS current_stock,
                   i.price AS unit_cost_current,
                   COUNT(DISTINCT r.id) AS num_recipes_using,
                   COUNT(DISTINCT r.mongo_product_id) AS num_products_using,
                   SUM(r.quantity_to_subtract) AS total_qty_in_recipes,
                   AVG(r.quantity_to_subtract) AS avg_qty_per_recipe,
                   COALESCE(
                       (SELECT SUM(ABS(im2.quantity))
                        FROM inventory_movements im2
                        WHERE im2.inventory_id = i.id
                          AND im2.movement_type = 'SALIDA'
                          AND im2.created_at >= NOW() - INTERVAL '30 days'),
                       0
                   ) AS consumed_last_30d,
                   COALESCE(
                       (SELECT SUM(ABS(im3.quantity))
                        FROM inventory_movements im3
                        WHERE im3.inventory_id = i.id
                          AND im3.movement_type = 'SALIDA'
                          AND im3.created_at >= NOW() - INTERVAL '7 days'),
                       0
                   ) AS consumed_last_7d
            FROM inventory i
            LEFT JOIN recipes r ON i.id = r.ingredient_id AND r.is_deleted = false
            WHERE i.is_deleted = false
            GROUP BY i.id, i.name, i.category, i.unit, i.stock_quantity, i.price
            ORDER BY i.id
            """;

    private static final String SQL_DEMANDA_INGREDIENTES = """
            SELECT o.id AS order_id,
                   DATE(o.created_at) AS order_date,
                   EXTRACT(DOW FROM o.created_at) AS day_of_week,
                   EXTRACT(HOUR FROM o.created_at) AS hour_of_day,
                   EXTRACT(WEEK FROM o.created_at) AS week_of_year,
                   EXTRACT(MONTH FROM o.created_at) AS month,
                   o.status,
                   o.total_price,
                   o.weather_temp_c,
                   o.weather_condition,
                   o.moment_of_day,
                   o.day_of_week AS day_name,
                   oi.mongo_product_id AS product_id,
                   oi.quantity AS quantity_ordered,
                   oi.price_at_moment,
                   r.ingredient_id,
                   i.name AS ingredient_name,
                   i.unit,
                   (r.quantity_to_subtract * oi.quantity) AS ingredient_consumed
            FROM orders o
            JOIN order_items oi ON o.id = oi.order_id
            JOIN recipes r ON oi.mongo_product_id = r.mongo_product_id AND r.is_deleted = false
            JOIN inventory i ON r.ingredient_id = i.id AND i.is_deleted = false
            WHERE o.status IN ('ENTREGADO', 'EN_CAMINO', 'PREPARADO', 'PAGO_VALIDADO', 'EN_COCINA')
            ORDER BY o.created_at, i.id
            """;

    private static final String SQL_CONSUMOS_CON_CONTEXTO = """
            SELECT r.ingredient_id,
                   i.name AS ingredient_name,
                   i.unit,
                   i.category,
                   DATE(o.created_at) AS consumption_date,
                   EXTRACT(DOW FROM o.created_at) AS day_of_week,
                   EXTRACT(WEEK FROM o.created_at) AS week_of_year,
                   EXTRACT(MONTH FROM o.created_at) AS month,
                   EXTRACT(YEAR FROM o.created_at) AS year,
                   SUM(r.quantity_to_subtract * oi.quantity) AS total_consumed_from_orders,
                   COUNT(DISTINCT o.id) AS num_orders,
                   AVG(o.weather_temp_c) AS avg_temp_c,
                   MODE() WITHIN GROUP (ORDER BY o.weather_condition) AS dominant_weather,
                   MODE() WITHIN GROUP (ORDER BY o.moment_of_day) AS dominant_moment,
                   SUM(o.total_price) AS total_revenue_day
            FROM orders o
            JOIN order_items oi ON o.id = oi.order_id
            JOIN recipes r ON oi.mongo_product_id = r.mongo_product_id AND r.is_deleted = false
            JOIN inventory i ON r.ingredient_id = i.id AND i.is_deleted = false
            WHERE o.status IN ('ENTREGADO', 'EN_CAMINO', 'PREPARADO', 'PAGO_VALIDADO', 'EN_COCINA')
            GROUP BY r.ingredient_id, i.name, i.unit, i.category,
                     DATE(o.created_at),
                     EXTRACT(DOW FROM o.created_at),
                     EXTRACT(WEEK FROM o.created_at),
                     EXTRACT(MONTH FROM o.created_at),
                     EXTRACT(YEAR FROM o.created_at)
            ORDER BY r.ingredient_id, consumption_date
            """;

    private final JdbcTemplate jdbcTemplate;
    private final MongoTemplate mongoTemplate;

    public AiTrainingDatasetExportService(JdbcTemplate jdbcTemplate, MongoTemplate mongoTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.mongoTemplate = mongoTemplate;
    }

    public byte[] buildZip(int slot) {
        Map<String, byte[]> files = switch (slot) {
            case 1 -> buildDataset01();
            case 2 -> buildDataset02();
            case 3 -> buildDataset03();
            default -> throw new IllegalArgumentException("Slot de dataset no válido.");
        };
        return zipEntries(files);
    }

    public String zipFileName(int slot) {
        return String.format("dataset_modelo_%02d.zip", slot);
    }

    private Map<String, byte[]> buildDataset01() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("ventas_historial.csv", sqlCsv(SQL_VENTAS_HISTORIAL,
                "order_id", "client_id", "product_id", "quantity", "sold_price", "status", "created_at",
                "weather_temp_c", "weather_condition", "moment_of_day", "day_of_week", "rating"));
        files.put("costos_productos.csv", sqlCsv(SQL_COSTOS_PRODUCTOS, "product_id", "cost_to_make"));
        files.put("productos_catalogo.csv", productosCatalogoCsv(false));
        files.put("interacciones_usuarios.csv", interaccionesCsv());
        return files;
    }

    private Map<String, byte[]> buildDataset02() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("transacciones_carrito.csv", sqlCsv(SQL_TRANSACCIONES_CARRITO, "order_id", "product_id"));
        files.put("costos_productos.csv", sqlCsv(SQL_COSTOS_PRODUCTOS, "product_id", "cost_to_make"));
        files.put("productos_referencia.csv", productosCatalogoCsv(false));
        return files;
    }

    private Map<String, byte[]> buildDataset03() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("movimientos_inventario.csv", sqlCsv(SQL_MOVIMIENTOS_INVENTARIO,
                "movement_id", "inventory_id", "ingredient_name", "ingredient_category", "unit", "quantity",
                "previous_stock", "new_stock", "unit_cost", "movement_type", "reason", "movement_date",
                "day_of_week", "hour_of_day", "year_month", "week_of_year", "created_at"));
        files.put("consumos_diarios.csv", sqlCsv(SQL_CONSUMOS_DIARIOS,
                "inventory_id", "ingredient_name", "ingredient_category", "unit", "consumption_date", "day_of_week",
                "week_of_year", "month", "year", "total_consumed", "num_consumption_events",
                "avg_consumption_per_event", "min_stock_reached", "max_stock_before"));
        files.put("historial_stock_diario.csv", sqlCsv(SQL_HISTORIAL_STOCK_DIARIO,
                "inventory_id", "ingredient_name", "unit", "category", "snapshot_date", "opening_stock",
                "closing_stock", "net_consumed_day"));
        files.put("reposiciones_historico.csv", sqlCsv(SQL_REPOSICIONES,
                "inventory_id", "ingredient_name", "unit", "restock_date", "restock_quantity", "unit_cost",
                "previous_stock", "new_stock", "reason", "day_of_week", "month", "year"));
        files.put("inventario_actual.csv", sqlCsv(SQL_INVENTARIO_ACTUAL,
                "inventory_id", "ingredient_name", "category", "unit", "current_stock", "unit_cost_current",
                "num_recipes_using", "num_products_using", "total_qty_in_recipes", "avg_qty_per_recipe",
                "consumed_last_30d", "consumed_last_7d"));
        files.put("demanda_ingredientes_por_pedido.csv", sqlCsv(SQL_DEMANDA_INGREDIENTES,
                "order_id", "order_date", "day_of_week", "hour_of_day", "week_of_year", "month", "status",
                "total_price", "weather_temp_c", "weather_condition", "moment_of_day", "day_name", "product_id",
                "quantity_ordered", "price_at_moment", "ingredient_id", "ingredient_name", "unit",
                "ingredient_consumed"));
        files.put("consumos_con_contexto.csv", sqlCsv(SQL_CONSUMOS_CON_CONTEXTO,
                "ingredient_id", "ingredient_name", "unit", "category", "consumption_date", "day_of_week",
                "week_of_year", "month", "year", "total_consumed_from_orders", "num_orders", "avg_temp_c",
                "dominant_weather", "dominant_moment", "total_revenue_day"));
        files.put("catalogo_productos.csv", productosCatalogoCsv(true));
        return files;
    }

    private byte[] sqlCsv(String sql, String... columns) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
        return DatasetCsvWriter.write(columns, rows);
    }

    private byte[] productosCatalogoCsv(boolean includeDeletedFlag) {
        Query query = includeDeletedFlag
                ? new Query()
                : new Query(Criteria.where("isDeleted").ne(true));
        List<Producto> productos = mongoTemplate.find(query, Producto.class);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Producto p : productos) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("_id", p.getId());
            row.put("name", p.getName());
            row.put("category", p.getCategory());
            row.put("price", p.getPrice());
            if (includeDeletedFlag) {
                row.put("isDeleted", p.isDeleted());
            }
            rows.add(row);
        }
        if (includeDeletedFlag) {
            return DatasetCsvWriter.write(new String[]{"_id", "name", "category", "price", "isDeleted"}, rows);
        }
        return DatasetCsvWriter.write(new String[]{"_id", "name", "category", "price"}, rows);
    }

    private byte[] interaccionesCsv() {
        List<UserInteraction> docs = mongoTemplate.findAll(UserInteraction.class);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (UserInteraction ui : docs) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", ui.getUserId());
            row.put("productId", ui.getProductId());
            row.put("action", ui.getAction());
            row.put("dwellTimeSeconds", ui.getDwellTimeSeconds());
            if (ui.getContext() != null) {
                row.put("context.temp", ui.getContext().getTemp());
                row.put("context.condition", ui.getContext().getCondition());
                String segment = ui.getContext().getSegment();
                if (segment == null || segment.isBlank()) {
                    segment = ui.getContext().getDay();
                }
                row.put("context.segment", segment);
            } else {
                row.put("context.temp", null);
                row.put("context.condition", null);
                row.put("context.segment", null);
            }
            row.put("timestamp", ui.getTimestamp());
            rows.add(row);
        }
        return DatasetCsvWriter.write(
                new String[]{
                        "userId", "productId", "action", "dwellTimeSeconds",
                        "context.temp", "context.condition", "context.segment", "timestamp"
                },
                rows
        );
    }

    private byte[] zipEntries(Map<String, byte[]> files) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zos.putNextEntry(zipEntry);
                zos.write(entry.getValue());
                zos.closeEntry();
            }
            zos.finish();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el archivo ZIP.", e);
        }
    }
}
