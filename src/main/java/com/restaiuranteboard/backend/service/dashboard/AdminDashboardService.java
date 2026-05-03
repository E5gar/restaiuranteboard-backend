package com.restaiuranteboard.backend.service.dashboard;

import com.restaiuranteboard.backend.model.nosql.AiModelConfig;
import com.restaiuranteboard.backend.model.nosql.Producto;
import com.restaiuranteboard.backend.model.nosql.UserInteraction;
import com.restaiuranteboard.backend.model.sql.*;
import com.restaiuranteboard.backend.repository.nosql.AiModelConfigRepository;
import com.restaiuranteboard.backend.repository.nosql.ProductoRepository;
import com.restaiuranteboard.backend.repository.nosql.UserInteractionRepository;
import com.restaiuranteboard.backend.repository.sql.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class AdminDashboardService {

    private static final List<String> ESTADOS_EN_CURSO = List.of(
            "PENDIENTE_PAGO",
            "VALIDANDO_PAGO",
            "PAGO_VALIDADO",
            "EN_COCINA",
            "PREPARADO",
            "EN_CAMINO"
    );

    private static final List<String> POST_VALIDACION = List.of(
            "PAGO_VALIDADO",
            "EN_COCINA",
            "PREPARADO",
            "EN_CAMINO",
            "ENTREGADO"
    );

    private static final List<String> DIAS_SEMANA = List.of(
            "LUNES", "MARTES", "MIERCOLES", "JUEVES", "VIERNES", "SABADO", "DOMINGO"
    );

    private final RestaurantOrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final RecipeRepository recipeRepository;
    private final ProductoRepository productoRepository;
    private final OrderRatingRepository orderRatingRepository;
    private final UserRepository userRepository;
    private final LoginAuditRepository loginAuditRepository;
    private final IpLoginAttemptRepository ipLoginAttemptRepository;
    private final UserInteractionRepository userInteractionRepository;
    private final AiModelConfigRepository aiModelConfigRepository;

    public AdminDashboardService(
            RestaurantOrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            InventoryRepository inventoryRepository,
            InventoryMovementRepository movementRepository,
            RecipeRepository recipeRepository,
            ProductoRepository productoRepository,
            OrderRatingRepository orderRatingRepository,
            UserRepository userRepository,
            LoginAuditRepository loginAuditRepository,
            IpLoginAttemptRepository ipLoginAttemptRepository,
            UserInteractionRepository userInteractionRepository,
            AiModelConfigRepository aiModelConfigRepository
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.recipeRepository = recipeRepository;
        this.productoRepository = productoRepository;
        this.orderRatingRepository = orderRatingRepository;
        this.userRepository = userRepository;
        this.loginAuditRepository = loginAuditRepository;
        this.ipLoginAttemptRepository = ipLoginAttemptRepository;
        this.userInteractionRepository = userInteractionRepository;
        this.aiModelConfigRepository = aiModelConfigRepository;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> ventasPedidos(
            LocalDateTime from,
            LocalDateTime toExclusive,
            String status,
            String momentOfDay,
            String dayOfWeek,
            String weatherCondition
    ) {
        Specification<RestaurantOrder> spec = baseSpec(from, toExclusive, status, momentOfDay, dayOfWeek, weatherCondition);
        List<RestaurantOrder> orders = orderRepository.findAll(spec);

        BigDecimal totalVentas = BigDecimal.ZERO;
        long nEntregados = 0;
        long nCancelados = 0;
        long nTotal = orders.size();
        Map<String, Long> porEstado = new HashMap<>();
        Map<String, BigDecimal> ventasPorDia = new TreeMap<>();
        Map<Integer, BigDecimal> ingresoPorHora = new TreeMap<>();
        Map<String, Long> pedidosPorDiaSemana = new LinkedHashMap<>();
        for (String d : DIAS_SEMANA) {
            pedidosPorDiaSemana.put(d, 0L);
        }
        double[][] heat = new double[24][7];
        Map<String, List<BigDecimal>> ticketPorSemana = new TreeMap<>();

        LocalDateTime now = LocalDateTime.now();
        LocalDate hoy = now.toLocalDate();

        long pedidosEnCursoGlobal = orderRepository.count((root, q, cb) -> root.get("status").in(ESTADOS_EN_CURSO));

        for (RestaurantOrder o : orders) {
            String st = o.getStatus() != null ? o.getStatus() : "";
            porEstado.merge(st, 1L, Long::sum);
            if ("ENTREGADO".equals(st)) {
                nEntregados++;
                BigDecimal tp = o.getTotalPrice() != null ? o.getTotalPrice() : BigDecimal.ZERO;
                totalVentas = totalVentas.add(tp);
                LocalDateTime ca = o.getCreatedAt();
                if (ca != null) {
                    String dayKey = ca.toLocalDate().toString();
                    ventasPorDia.merge(dayKey, tp, BigDecimal::add);
                    int h = ca.getHour();
                    ingresoPorHora.merge(h, tp, BigDecimal::add);
                    String dw = o.getDayOfWeek();
                    if (dw != null && pedidosPorDiaSemana.containsKey(dw)) {
                        pedidosPorDiaSemana.merge(dw, 1L, Long::sum);
                    }
                    int dowIdx;
                    if (dw != null && DIAS_SEMANA.contains(dw)) {
                        dowIdx = DIAS_SEMANA.indexOf(dw);
                    } else {
                        dowIdx = Math.max(0, Math.min(6, ca.getDayOfWeek().getValue() - 1));
                    }
                    heat[h][dowIdx] += tp.doubleValue();

                    LocalDate semana = ca.toLocalDate().with(java.time.DayOfWeek.MONDAY);
                    ticketPorSemana.computeIfAbsent(semana.toString(), k -> new ArrayList<>()).add(tp);
                }
            }
            if ("CANCELADO".equals(st)) {
                nCancelados++;
            }
        }

        double ticketPromedio = nEntregados > 0 ? totalVentas.divide(BigDecimal.valueOf(nEntregados), 2, RoundingMode.HALF_UP).doubleValue() : 0;
        double tasaCancelacion = nTotal > 0 ? round2(100.0 * nCancelados / nTotal) : 0;
        long cerradosOCancel = nEntregados + nCancelados;
        double conversion = cerradosOCancel > 0 ? round2(100.0 * nEntregados / cerradosOCancel) : 0;

        long pedidosHoy = orders.stream()
                .filter(o -> o.getCreatedAt() != null && hoy.equals(o.getCreatedAt().toLocalDate()))
                .count();
        BigDecimal ingresoHoy = BigDecimal.ZERO;
        for (RestaurantOrder o : orders) {
            if (!"ENTREGADO".equals(o.getStatus())) continue;
            LocalDate ref = o.getDeliveredAt() != null
                    ? o.getDeliveredAt().toLocalDate()
                    : (o.getCreatedAt() != null ? o.getCreatedAt().toLocalDate() : null);
            if (ref != null && ref.equals(hoy)) {
                ingresoHoy = ingresoHoy.add(o.getTotalPrice() != null ? o.getTotalPrice() : BigDecimal.ZERO);
            }
        }

        List<Map<String, Object>> ticketSemanal = new ArrayList<>();
        for (Map.Entry<String, List<BigDecimal>> e : ticketPorSemana.entrySet()) {
            BigDecimal sum = e.getValue().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            double avg = e.getValue().isEmpty() ? 0 : sum.divide(BigDecimal.valueOf(e.getValue().size()), 2, RoundingMode.HALF_UP).doubleValue();
            ticketSemanal.add(row("semana", e.getKey(), "ticketPromedio", avg));
        }

        List<Map<String, Object>> heatList = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            for (int d = 0; d < 7; d++) {
                heatList.add(row("hora", h, "diaIndex", d, "valor", heat[h][d]));
            }
        }

        List<Map<String, Object>> climaVsMonto = new ArrayList<>();
        for (RestaurantOrder o : orders) {
            if (!"ENTREGADO".equals(o.getStatus()) || o.getWeatherTempC() == null) continue;
            climaVsMonto.add(row(
                    "tempC", o.getWeatherTempC(),
                    "monto", o.getTotalPrice() != null ? o.getTotalPrice().doubleValue() : 0.0
            ));
            if (climaVsMonto.size() >= 400) break;
        }

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("totalVentas", totalVentas.setScale(2, RoundingMode.HALF_UP).doubleValue());
        kpis.put("numPedidos", nTotal);
        kpis.put("ticketPromedio", ticketPromedio);
        kpis.put("tasaCancelacionPct", tasaCancelacion);
        kpis.put("conversionPct", conversion);
        kpis.put("pedidosHoy", pedidosHoy);
        kpis.put("ingresoHoy", ingresoHoy.setScale(2, RoundingMode.HALF_UP).doubleValue());
        kpis.put("pedidosEnCurso", pedidosEnCursoGlobal);

        return Map.of(
                "kpis", kpis,
                "pedidosPorEstado", porEstado,
                "ventasPorDia", ventasPorDia,
                "ingresoPorHora", ingresoPorHora.entrySet().stream()
                        .map(e -> row("hora", e.getKey(), "monto", e.getValue().setScale(2, RoundingMode.HALF_UP).doubleValue()))
                        .toList(),
                "pedidosPorDiaSemana", pedidosPorDiaSemana,
                "evolucionTicketSemanal", ticketSemanal,
                "heatmapHoraDia", heatList,
                "climaTemperaturaVsMonto", climaVsMonto
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> inventarioCostos(
            LocalDateTime from,
            LocalDateTime toExclusive,
            String categoriaInsumo,
            String tipoMovimiento,
            boolean soloStockBajo,
            double umbralStockBajo
    ) {
        List<Inventory> invAll = inventoryRepository.findAllByIsDeletedFalse();
        List<Inventory> inv = invAll.stream()
                .filter(i -> categoriaInsumo == null || categoriaInsumo.isBlank()
                        || (i.getCategory() != null && i.getCategory().equalsIgnoreCase(categoriaInsumo.trim())))
                .toList();

        BigDecimal valorInventario = BigDecimal.ZERO;
        long stockBajo = 0;
        List<Map<String, Object>> stockPorInsumo = new ArrayList<>();
        for (Inventory i : inv) {
            double stock = i.getStockQuantity() != null ? i.getStockQuantity() : 0;
            double price = i.getPrice() != null ? i.getPrice() : 0;
            BigDecimal val = BigDecimal.valueOf(stock).multiply(BigDecimal.valueOf(price)).setScale(2, RoundingMode.HALF_UP);
            valorInventario = valorInventario.add(val);
            if (stock < umbralStockBajo) {
                stockBajo++;
            }
            stockPorInsumo.add(row(
                    "id", i.getId(),
                    "nombre", i.getName() != null ? i.getName() : "",
                    "stock", stock,
                    "umbral", umbralStockBajo,
                    "categoria", i.getCategory() != null ? i.getCategory() : "",
                    "valor", val.doubleValue()
            ));
        }
        if (soloStockBajo) {
            stockPorInsumo = stockPorInsumo.stream().filter(m -> (Double) m.get("stock") < umbralStockBajo).toList();
        }

        List<InventoryMovement> movs = movementRepository.findByCreatedAtBetween(from, toExclusive).stream()
                .filter(m -> tipoMovimiento == null || tipoMovimiento.isBlank()
                        || (m.getMovementType() != null && m.getMovementType().equalsIgnoreCase(tipoMovimiento.trim())))
                .toList();

        BigDecimal costoSalida = BigDecimal.ZERO;
        BigDecimal totalAbastecido = BigDecimal.ZERO;
        Map<String, BigDecimal> consumoPorInsumo = new HashMap<>();
        Map<String, BigDecimal> abastPorSemana = new TreeMap<>();

        for (InventoryMovement m : movs) {
            LocalDateTime ca = m.getCreatedAt();
            BigDecimal qty = m.getQuantity() != null ? m.getQuantity() : BigDecimal.ZERO;
            BigDecimal uc = m.getUnitCost() != null ? m.getUnitCost() : BigDecimal.ZERO;
            if ("SALIDA".equalsIgnoreCase(String.valueOf(m.getMovementType()))) {
                BigDecimal cost = qty.multiply(uc).setScale(2, RoundingMode.HALF_UP);
                costoSalida = costoSalida.add(cost);
                consumoPorInsumo.merge(String.valueOf(m.getInventoryId()), qty, BigDecimal::add);
            }
            if ("ABASTECIMIENTO".equalsIgnoreCase(String.valueOf(m.getMovementType()))) {
                BigDecimal cost = qty.multiply(uc != null ? uc : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                totalAbastecido = totalAbastecido.add(cost);
                if (ca != null) {
                    String sem = ca.toLocalDate().with(java.time.DayOfWeek.MONDAY).toString();
                    abastPorSemana.merge(sem, cost, BigDecimal::add);
                }
            }
        }

        double stockPromedio = inv.isEmpty() ? 0 : inv.stream().mapToDouble(i -> i.getStockQuantity() != null ? i.getStockQuantity() : 0).average().orElse(0);
        double salidaQty = movs.stream()
                .filter(m -> "SALIDA".equalsIgnoreCase(String.valueOf(m.getMovementType())))
                .mapToDouble(m -> m.getQuantity() != null ? m.getQuantity().doubleValue() : 0)
                .sum();
        double rotacion = stockPromedio > 0 ? round2(salidaQty / stockPromedio) : 0;

        LocalDateTime cutoff = from.minusDays(30);
        Set<Integer> conMovimientoReciente = movs.stream()
                .filter(m -> m.getCreatedAt() != null && m.getCreatedAt().isAfter(cutoff))
                .map(InventoryMovement::getInventoryId)
                .collect(Collectors.toSet());
        long sinMovimiento = inv.stream()
                .mapToLong(i -> conMovimientoReciente.contains(i.getId()) ? 0 : 1)
                .sum();

        Map<Integer, String> idToCat = inv.stream().collect(Collectors.toMap(Inventory::getId, i -> i.getCategory() != null ? i.getCategory() : "", (a, b) -> a));
        Map<String, BigDecimal> consumoCat = new HashMap<>();
        for (InventoryMovement m : movs) {
            if (!"SALIDA".equalsIgnoreCase(String.valueOf(m.getMovementType()))) continue;
            Integer invId = m.getInventoryId();
            if (invId == null) continue;
            BigDecimal qty = m.getQuantity() != null ? m.getQuantity() : BigDecimal.ZERO;
            BigDecimal uc = m.getUnitCost() != null ? m.getUnitCost() : BigDecimal.ZERO;
            BigDecimal cost = qty.multiply(uc).setScale(2, RoundingMode.HALF_UP);
            String cat = idToCat.getOrDefault(invId, "OTRO");
            consumoCat.merge(cat, cost, BigDecimal::add);
        }

        List<Map<String, Object>> margenProductos = new ArrayList<>();
        for (Producto p : productoRepository.findByIsDeletedFalse()) {
            if (p.getId() == null) continue;
            double precio = p.getPrice() != null ? p.getPrice() : 0;
            double costoReceta = costoRecetaUnitario(p.getId());
            margenProductos.add(row(
                    "productoId", p.getId(),
                    "nombre", p.getName() != null ? p.getName() : "",
                    "precioVenta", precio,
                    "costoReceta", round2(costoReceta),
                    "margenBruto", round2(precio - costoReceta)
            ));
        }
        margenProductos.sort((a, b) -> Double.compare((Double) b.get("margenBruto"), (Double) a.get("margenBruto")));

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("insumosStockBajo", stockBajo);
        kpis.put("valorTotalInventario", valorInventario.setScale(2, RoundingMode.HALF_UP).doubleValue());
        kpis.put("costoMateriaConsumida", costoSalida.setScale(2, RoundingMode.HALF_UP).doubleValue());
        kpis.put("totalAbastecidoPeriodo", totalAbastecido.setScale(2, RoundingMode.HALF_UP).doubleValue());
        kpis.put("insumosSinMovimiento", sinMovimiento);
        kpis.put("rotacionInventario", rotacion);

        return Map.of(
                "kpis", kpis,
                "stockPorInsumo", stockPorInsumo,
                "movimientosAbastecimientoPorSemana", abastPorSemana,
                "topConsumoInsumo", topN(consumoPorInsumo, 10),
                "consumoPorCategoria", consumoCat,
                "margenBrutoProductos", margenProductos.stream().limit(20).toList()
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> productos(
            LocalDateTime from,
            LocalDateTime toExclusive,
            String categoriaProducto,
            Integer estrellasMin,
            Double precioMin,
            Double precioMax
    ) {
        List<RestaurantOrder> entregados = orderRepository.findAll(
                Specification.where(RestaurantOrderSpecs.createdBetween(from, toExclusive))
                        .and((root, q, cb) -> cb.equal(root.get("status"), "ENTREGADO"))
        );
        Set<UUID> ids = entregados.stream().map(RestaurantOrder::getId).collect(Collectors.toSet());
        Map<String, Integer> qtyPorProducto = new HashMap<>();
        Map<String, BigDecimal> ingresoPorProducto = new HashMap<>();
        if (!ids.isEmpty()) {
            for (OrderItem oi : orderItemRepository.findByRestaurantOrder_IdIn(ids)) {
                String pid = oi.getMongoProductId();
                int q = oi.getQuantity() != null ? oi.getQuantity() : 0;
                qtyPorProducto.merge(pid, q, Integer::sum);
                BigDecimal sub = oi.getPriceAtMoment() != null
                        ? oi.getPriceAtMoment().multiply(BigDecimal.valueOf(q))
                        : BigDecimal.ZERO;
                ingresoPorProducto.merge(pid, sub, BigDecimal::add);
            }
        }

        Map<String, Producto> prodMap = productoRepository.findByIsDeletedFalse().stream()
                .filter(p -> p.getId() != null)
                .collect(Collectors.toMap(Producto::getId, p -> p, (a, b) -> a));

        List<Map<String, Object>> ranking = new ArrayList<>();
        for (Map.Entry<String, Integer> e : qtyPorProducto.entrySet()) {
            Producto p = prodMap.get(e.getKey());
            if (p == null) continue;
            if (categoriaProducto != null && !categoriaProducto.isBlank()
                    && (p.getCategory() == null || !p.getCategory().equalsIgnoreCase(categoriaProducto.trim()))) {
                continue;
            }
            double price = p.getPrice() != null ? p.getPrice() : 0;
            if (precioMin != null && price < precioMin) continue;
            if (precioMax != null && price > precioMax) continue;
            double margen = price - costoRecetaUnitario(p.getId());
            ranking.add(row(
                    "productoId", p.getId(),
                    "nombre", p.getName() != null ? p.getName() : "",
                    "categoria", p.getCategory() != null ? p.getCategory() : "",
                    "unidadesVendidas", e.getValue(),
                    "ingresos", ingresoPorProducto.getOrDefault(p.getId(), BigDecimal.ZERO).doubleValue(),
                    "margenEstimado", round2(margen * e.getValue())
            ));
        }
        ranking.sort((a, b) -> Integer.compare((Integer) b.get("unidadesVendidas"), (Integer) a.get("unidadesVendidas")));

        String masVendido = ranking.isEmpty() ? "" : String.valueOf(ranking.get(0).get("nombre"));
        String masRentable = ranking.stream()
                .max(Comparator.comparingDouble(m -> (Double) m.get("margenEstimado")))
                .map(m -> String.valueOf(m.get("nombre")))
                .orElse("");

        Map<String, BigDecimal> ingresoPorCat = new HashMap<>();
        for (Map<String, Object> row : ranking) {
            String cat = String.valueOf(row.get("categoria"));
            BigDecimal ing = BigDecimal.valueOf((Double) row.get("ingresos"));
            ingresoPorCat.merge(cat, ing, BigDecimal::add);
        }

        long activos = productoRepository.findByIsDeletedFalse().size();
        Double avgStars = orderRatingRepository.avgStarsBetween(from, toExclusive);
        double avgStarsVal = avgStars != null ? round2(avgStars) : 0;

        long entregadosCount = entregados.size();
        long ratedCount = entregados.stream().filter(o -> Boolean.TRUE.equals(o.getIsRated())).count();
        double tasaCalificados = entregadosCount > 0 ? round2(100.0 * ratedCount / entregadosCount) : 0;

        String catLider = ingresoPorCat.entrySet().stream()
                .max(Comparator.comparing(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse("");

        Map<Integer, Long> distEstrellas = new TreeMap<>();
        for (int s = 1; s <= 5; s++) {
            distEstrellas.put(s, 0L);
        }
        for (Object[] pair : orderRatingRepository.countStarsGroupedBetween(from, toExclusive)) {
            int stars = (Integer) pair[0];
            long cnt = (Long) pair[1];
            if (stars >= 1 && stars <= 5) {
                distEstrellas.merge(stars, cnt, Long::sum);
            }
        }

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("productosActivos", activos);
        kpis.put("productoMasVendido", masVendido);
        kpis.put("productoMasRentable", masRentable);
        kpis.put("calificacionPromedio", avgStarsVal);
        kpis.put("categoriaLiderVentas", catLider);
        kpis.put("tasaPedidosCalificadosPct", tasaCalificados);

        return Map.of(
                "kpis", kpis,
                "topProductos", ranking.stream().limit(15).toList(),
                "ingresosPorCategoria", ingresoPorCat,
                "distribucionEstrellas", distEstrellas
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> clientes(
            LocalDateTime from,
            LocalDateTime toExclusive,
            LocalDateTime regFrom,
            LocalDateTime regToExclusive,
            Integer estrellasFiltro,
            Boolean soloRecurrentes
    ) {
        long totalClientes = userRepository.findAll().stream()
                .filter(u -> !u.isDeleted() && u.getRole() != null && "CLIENTE".equals(u.getRole().getName()))
                .count();

        LocalDateTime r0 = regFrom != null ? regFrom : from;
        LocalDateTime r1 = regToExclusive != null ? regToExclusive : toExclusive;
        long nuevos = userRepository.countByRole_NameAndIsDeletedFalseAndCreatedAtBetween("CLIENTE", r0, r1);

        List<RestaurantOrder> entregados = orderRepository.findAll(
                Specification.where(RestaurantOrderSpecs.createdBetween(from, toExclusive))
                        .and((root, q, cb) -> cb.equal(root.get("status"), "ENTREGADO"))
        );
        Map<UUID, Long> pedidosPorCliente = entregados.stream()
                .filter(o -> o.getClient() != null && o.getClient().getId() != null)
                .collect(Collectors.groupingBy(o -> o.getClient().getId(), Collectors.counting()));
        long recurrentes = pedidosPorCliente.values().stream().filter(c -> c > 1).count();
        double tasaRecurrencia = totalClientes > 0 ? round2(100.0 * recurrentes / totalClientes) : 0;

        Double avgStars = orderRatingRepository.avgStarsBetween(from, toExclusive);
        double pedidosPromedio = totalClientes > 0 ? round2((double) entregados.size() / totalClientes) : 0;

        Stream<Map.Entry<UUID, Long>> streamClientes = pedidosPorCliente.entrySet().stream();
        if (Boolean.TRUE.equals(soloRecurrentes)) {
            streamClientes = streamClientes.filter(e -> e.getValue() > 1);
        }

        List<Map<String, Object>> topGasto = streamClientes
                .map(e -> {
                    UUID uid = e.getKey();
                    User u = userRepository.findById(uid).orElse(null);
                    BigDecimal gasto = entregados.stream()
                            .filter(o -> o.getClient() != null && uid.equals(o.getClient().getId()))
                            .map(o -> o.getTotalPrice() != null ? o.getTotalPrice() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return row(
                            "clienteId", uid.toString(),
                            "nombre", u != null && u.getFullName() != null ? u.getFullName() : "",
                            "pedidos", e.getValue(),
                            "gastoTotal", gasto.setScale(2, RoundingMode.HALF_UP).doubleValue()
                    );
                })
                .sorted((a, b) -> Double.compare((Double) b.get("gastoTotal"), (Double) a.get("gastoTotal")))
                .limit(10)
                .toList();

        Map<Integer, Long> distEstrellasCli = new TreeMap<>();
        for (int s = 1; s <= 5; s++) {
            distEstrellasCli.put(s, 0L);
        }
        for (Object[] pair : orderRatingRepository.countStarsGroupedBetween(from, toExclusive)) {
            int stars = (Integer) pair[0];
            long cnt = (Long) pair[1];
            if (stars >= 1 && stars <= 5) {
                distEstrellasCli.merge(stars, cnt, Long::sum);
            }
        }

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("totalClientes", totalClientes);
        kpis.put("clientesNuevosPeriodo", nuevos);
        kpis.put("clientesRecurrentes", recurrentes);
        kpis.put("tasaRecurrenciaPct", tasaRecurrencia);
        kpis.put("calificacionPromedioGeneral", avgStars != null ? round2(avgStars) : 0);
        kpis.put("pedidosPromedioPorCliente", pedidosPromedio);

        return Map.of(
                "kpis", kpis,
                "topClientesGasto", topGasto,
                "frecuenciaPedidosHistograma", histogramaPedidos(pedidosPorCliente),
                "distribucionEstrellas", distEstrellasCli
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> operacion(
            LocalDateTime from,
            LocalDateTime toExclusive,
            UUID cajeroId,
            UUID repartidorId
    ) {
        List<RestaurantOrder> list = orderRepository.findAll(RestaurantOrderSpecs.createdBetween(from, toExclusive));
        if (cajeroId != null) {
            list = list.stream().filter(o -> o.getProcessedBy() != null && cajeroId.equals(o.getProcessedBy().getId())).toList();
        }
        if (repartidorId != null) {
            list = list.stream().filter(o -> o.getDeliveryPerson() != null && repartidorId.equals(o.getDeliveryPerson().getId())).toList();
        }

        List<Double> minutosEntrega = new ArrayList<>();
        List<Double> minutosDecisionCajaCancel = new ArrayList<>();
        for (RestaurantOrder o : list) {
            if ("ENTREGADO".equals(o.getStatus()) && o.getDeliveredAt() != null && o.getDeliveryAssignedAt() != null) {
                long m = ChronoUnit.MINUTES.between(o.getDeliveryAssignedAt(), o.getDeliveredAt());
                if (m >= 0 && m < 24 * 60) {
                    minutosEntrega.add((double) m);
                }
            }
            if ("CANCELADO".equals(o.getStatus()) && o.getProcessedBy() != null && o.getCreatedAt() != null && o.getProcessedAt() != null) {
                long mc = ChronoUnit.MINUTES.between(o.getCreatedAt(), o.getProcessedAt());
                if (mc >= 0 && mc < 7 * 24 * 60) {
                    minutosDecisionCajaCancel.add((double) mc);
                }
            }
        }

        Map<String, Long> buckets = new LinkedHashMap<>();
        buckets.put("0-20", 0L);
        buckets.put("20-40", 0L);
        buckets.put("40-60", 0L);
        buckets.put("60+", 0L);
        for (Double m : minutosEntrega) {
            if (m < 20) buckets.merge("0-20", 1L, Long::sum);
            else if (m < 40) buckets.merge("20-40", 1L, Long::sum);
            else if (m < 60) buckets.merge("40-60", 1L, Long::sum);
            else buckets.merge("60+", 1L, Long::sum);
        }

        Map<String, Long> entregasPorRepartidor = list.stream()
                .filter(o -> "ENTREGADO".equals(o.getStatus()) && o.getDeliveryPerson() != null)
                .collect(Collectors.groupingBy(o -> o.getDeliveryPerson().getFullName() != null ? o.getDeliveryPerson().getFullName() : "—", Collectors.counting()));

        Map<String, Long> validadosPorCajero = list.stream()
                .filter(o -> o.getProcessedBy() != null && POST_VALIDACION.contains(o.getStatus()))
                .collect(Collectors.groupingBy(o -> o.getProcessedBy().getFullName() != null ? o.getProcessedBy().getFullName() : "—", Collectors.counting()));

        Map<String, Long> rechazadosPorCajero = list.stream()
                .filter(o -> o.getProcessedBy() != null && "CANCELADO".equals(o.getStatus()))
                .collect(Collectors.groupingBy(o -> o.getProcessedBy().getFullName() != null ? o.getProcessedBy().getFullName() : "—", Collectors.counting()));

        Set<String> nombresCajero = new HashSet<>();
        nombresCajero.addAll(validadosPorCajero.keySet());
        nombresCajero.addAll(rechazadosPorCajero.keySet());
        List<Map<String, Object>> cajeroFilas = new ArrayList<>();
        for (String nombre : nombresCajero.stream().sorted().toList()) {
            cajeroFilas.add(row(
                    "cajero", nombre,
                    "validados", validadosPorCajero.getOrDefault(nombre, 0L),
                    "rechazados", rechazadosPorCajero.getOrDefault(nombre, 0L)
            ));
        }

        long enCocina = orderRepository.count((root, q, cb) -> cb.equal(root.get("status"), "EN_COCINA"));

        double avgEntrega = minutosEntrega.isEmpty() ? 0 : round2(minutosEntrega.stream().mapToDouble(Double::doubleValue).average().orElse(0));
        double avgDecisionCancel = minutosDecisionCajaCancel.isEmpty() ? 0 : round2(minutosDecisionCajaCancel.stream().mapToDouble(Double::doubleValue).average().orElse(0));

        LocalDate hoy = LocalDate.now();
        long pedidosSuperaronValidacionHoy = list.stream()
                .filter(o -> o.getCreatedAt() != null && hoy.equals(o.getCreatedAt().toLocalDate()))
                .filter(o -> POST_VALIDACION.contains(o.getStatus()))
                .count();

        Map<Integer, Map<String, Long>> porHoraEstado = new TreeMap<>();
        for (RestaurantOrder o : list) {
            if (o.getCreatedAt() == null || o.getStatus() == null) continue;
            int hr = o.getCreatedAt().getHour();
            porHoraEstado.computeIfAbsent(hr, k -> new HashMap<>())
                    .merge(o.getStatus(), 1L, Long::sum);
        }
        List<Map<String, Object>> embudoPorHora = new ArrayList<>();
        for (Map.Entry<Integer, Map<String, Long>> e : porHoraEstado.entrySet()) {
            embudoPorHora.add(row("hora", e.getKey(), "porEstado", e.getValue()));
        }

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("tiempoPromedioValidacionMin", avgDecisionCancel);
        kpis.put("tiempoPromedioCocinaMin", 0);
        kpis.put("tiempoPromedioEntregaMin", avgEntrega);
        kpis.put("pedidosSuperaronValidacionHoy", pedidosSuperaronValidacionHoy);
        kpis.put("pedidosEnCocinaAhora", enCocina);

        return Map.of(
                "kpis", kpis,
                "histogramaTiemposEntrega", buckets,
                "entregasPorRepartidor", entregasPorRepartidor,
                "cajeroValidadosVsRechazados", cajeroFilas,
                "embudoPorHora", embudoPorHora
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> seguridad(LocalDateTime from, LocalDateTime toExclusive, String status, String rol) {
        Stream<LoginAudit> auditStream = loginAuditRepository.findByAttemptedAtBetween(from, toExclusive).stream()
                .filter(a -> status == null || status.isBlank() || status.equalsIgnoreCase(a.getStatus()));
        if (rol != null && !rol.isBlank()) {
            auditStream = auditStream.filter(a -> userRepository.findByEmail(a.getUserEmail())
                    .map(u -> u.getRole() != null && rol.trim().equalsIgnoreCase(u.getRole().getName()))
                    .orElse(false));
        }
        List<LoginAudit> audits = auditStream.toList();

        long total = audits.size();
        long success = audits.stream().filter(a -> "SUCCESS".equalsIgnoreCase(a.getStatus())).count();
        long failed = audits.stream().filter(a -> "FAILED".equalsIgnoreCase(a.getStatus())).count();
        long blocked = audits.stream().filter(a -> "BLOCKED".equalsIgnoreCase(a.getStatus())).count();
        double tasaExito = total > 0 ? round2(100.0 * success / total) : 0;

        LocalDateTime now = LocalDateTime.now();
        long ipsBloqueadas = ipLoginAttemptRepository.findAll().stream()
                .filter(ip -> ip.getBlockedUntil() != null && ip.getBlockedUntil().isAfter(now))
                .count();

        long usuariosUnicos = audits.stream()
                .filter(a -> "SUCCESS".equalsIgnoreCase(a.getStatus()))
                .map(LoginAudit::getUserEmail)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        Map<Integer, long[]> porHora = new HashMap<>();
        for (int h = 0; h < 24; h++) {
            porHora.put(h, new long[]{0, 0, 0});
        }
        for (LoginAudit a : audits) {
            if (a.getAttemptedAt() == null) continue;
            int h = a.getAttemptedAt().getHour();
            long[] arr = porHora.get(h);
            if ("SUCCESS".equalsIgnoreCase(a.getStatus())) arr[0]++;
            else if ("FAILED".equalsIgnoreCase(a.getStatus())) arr[1]++;
            else if ("BLOCKED".equalsIgnoreCase(a.getStatus())) arr[2]++;
        }

        List<Map<String, Object>> ipFallos = ipLoginAttemptRepository.findAll().stream()
                .sorted(Comparator.comparing(IpLoginAttempt::getFailedAttempts, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .map(ip -> row(
                        "ip", ip.getIpAddress(),
                        "fallos", ip.getFailedAttempts() != null ? ip.getFailedAttempts() : 0
                ))
                .toList();

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("totalIntentos", total);
        kpis.put("tasaExitoPct", tasaExito);
        kpis.put("ipsBloqueadasActivas", ipsBloqueadas);
        kpis.put("intentosFallidos", failed);
        kpis.put("usuariosUnicosActivos", usuariosUnicos);
        kpis.put("eventosBloqueo", blocked);

        return Map.of(
                "kpis", kpis,
                "intentosPorHora", porHora.entrySet().stream()
                        .map(e -> row(
                                "hora", e.getKey(),
                                "success", e.getValue()[0],
                                "failed", e.getValue()[1],
                                "blocked", e.getValue()[2]
                        ))
                        .toList(),
                "ipsMasFallos", ipFallos
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> interacciones(
            LocalDateTime from,
            LocalDateTime toExclusive,
            String action,
            String condicionClima,
            String segmento,
            String userId
    ) {
        List<UserInteraction> all = userInteractionRepository.findByTimestampBetween(from, toExclusive).stream()
                .filter(i -> action == null || action.isBlank() || action.equalsIgnoreCase(i.getAction()))
                .filter(i -> userId == null || userId.isBlank() || userId.equals(i.getUserId()))
                .filter(i -> {
                    if (condicionClima == null || condicionClima.isBlank()) return true;
                    return i.getContext() != null && condicionClima.equalsIgnoreCase(i.getContext().getCondition());
                })
                .filter(i -> {
                    if (segmento == null || segmento.isBlank()) return true;
                    return i.getContext() != null && segmento.equalsIgnoreCase(i.getContext().getSegment());
                })
                .toList();

        long total = all.size();
        Map<String, Long> porAccion = all.stream().collect(Collectors.groupingBy(i -> i.getAction() != null ? i.getAction() : "?", Collectors.counting()));
        long views = porAccion.getOrDefault("VIEW_DETAIL", 0L);
        long adds = porAccion.getOrDefault("ADD_TO_CART", 0L);
        double tasaAdd = views > 0 ? round2(100.0 * adds / views) : 0;
        long rejects = porAccion.getOrDefault("REJECT_RECOMMENDATION", 0L);
        double tasaReject = total > 0 ? round2(100.0 * rejects / total) : 0;
        double dwellAvg = all.stream()
                .filter(i -> i.getDwellTimeSeconds() != null)
                .mapToInt(UserInteraction::getDwellTimeSeconds)
                .average().orElse(0);

        Map<String, Long> porProducto = all.stream()
                .filter(i -> i.getProductId() != null && !i.getProductId().isBlank())
                .collect(Collectors.groupingBy(UserInteraction::getProductId, Collectors.counting()));

        String slot1 = aiModelConfigRepository.findById("GLOBAL_AI_CONFIG")
                .map(cfg -> cfg.getSlots().stream().filter(s -> s.getSlotNumber() == 1).findFirst().map(AiModelConfig.ModelSlot::getStatus).orElse("VACIO"))
                .orElse("VACIO");

        Map<String, Map<String, Long>> climaPorAccion = new LinkedHashMap<>();
        Map<String, Long> porSegmento = new LinkedHashMap<>();
        for (UserInteraction i : all) {
            String cond = i.getContext() != null && i.getContext().getCondition() != null ? i.getContext().getCondition() : "—";
            String act = i.getAction() != null ? i.getAction() : "?";
            climaPorAccion.computeIfAbsent(cond, k -> new LinkedHashMap<>()).merge(act, 1L, Long::sum);
            String seg = i.getContext() != null && i.getContext().getSegment() != null ? i.getContext().getSegment() : "—";
            porSegmento.merge(seg, 1L, Long::sum);
        }

        Map<String, Object> kpis = new LinkedHashMap<>();
        kpis.put("interaccionesTotales", total);
        kpis.put("tasaAddToCartPct", tasaAdd);
        kpis.put("tasaRechazoRecomendacionPct", tasaReject);
        kpis.put("dwellTimePromedioSeg", round2(dwellAvg));
        kpis.put("estadoSlot1Ia", slot1);

        return Map.of(
                "kpis", kpis,
                "distribucionAcciones", porAccion,
                "topProductosInteraccion", topNString(porProducto, 10),
                "productoMasVistoNombre", nombreProductoMasVisto(porProducto),
                "porCondicionClimaYAccion", climaPorAccion,
                "porSegmentoDia", porSegmento
        );
    }

    private String nombreProductoMasVisto(Map<String, Long> porProducto) {
        return porProducto.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .flatMap(e -> productoRepository.findById(e.getKey()).map(Producto::getName))
                .orElse("");
    }

    private Specification<RestaurantOrder> baseSpec(
            LocalDateTime from,
            LocalDateTime toExclusive,
            String status,
            String momentOfDay,
            String dayOfWeek,
            String weatherCondition
    ) {
        return Specification.where(RestaurantOrderSpecs.createdBetween(from, toExclusive))
                .and(RestaurantOrderSpecs.statusEquals(status))
                .and(RestaurantOrderSpecs.momentOfDayEquals(momentOfDay))
                .and(RestaurantOrderSpecs.dayOfWeekEquals(dayOfWeek))
                .and(RestaurantOrderSpecs.weatherConditionEquals(weatherCondition));
    }

    private double costoRecetaUnitario(String mongoProductId) {
        List<Recipe> lines = recipeRepository.findByMongoProductIdAndIsDeletedFalse(mongoProductId);
        double sum = 0;
        for (Recipe r : lines) {
            Inventory ing = r.getIngredient();
            if (ing == null) continue;
            double unitCost = ing.getPrice() != null ? ing.getPrice() : 0;
            double q = r.getQuantityToSubtract() != null ? r.getQuantityToSubtract() : 0;
            sum += q * unitCost;
        }
        return sum;
    }

    private static List<Map<String, Object>> topN(Map<String, BigDecimal> map, int n) {
        return map.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(n)
                .map(e -> row("insumoId", e.getKey(), "cantidad", e.getValue().doubleValue()))
                .toList();
    }

    private static List<Map<String, Object>> topNString(Map<String, Long> map, int n) {
        return map.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(n)
                .map(e -> row("productoId", e.getKey(), "interacciones", e.getValue()))
                .toList();
    }

    private static Map<String, Object> row(Object... kv) {
        if (kv.length % 2 != 0) {
            throw new IllegalArgumentException();
        }
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static Map<String, Long> histogramaPedidos(Map<UUID, Long> pedidosPorCliente) {
        Map<String, Long> h = new LinkedHashMap<>();
        h.put("1", 0L);
        h.put("2", 0L);
        h.put("3", 0L);
        h.put("4+", 0L);
        for (long c : pedidosPorCliente.values()) {
            if (c <= 1) h.merge("1", 1L, Long::sum);
            else if (c == 2) h.merge("2", 1L, Long::sum);
            else if (c == 3) h.merge("3", 1L, Long::sum);
            else h.merge("4+", 1L, Long::sum);
        }
        return h;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
