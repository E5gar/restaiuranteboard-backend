package com.restaiuranteboard.backend.controller;

import com.restaiuranteboard.backend.service.dashboard.AdminDashboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    private static LocalDateTime fromDef(LocalDateTime from) {
        return from != null ? from : LocalDateTime.now().minusDays(30).withHour(0).withMinute(0).withSecond(0).withNano(0);
    }

    private static LocalDateTime toDefExclusive(LocalDateTime to) {
        return to != null ? to : LocalDateTime.now().plusMinutes(1);
    }

    @GetMapping("/ventas-pedidos")
    public ResponseEntity<Map<String, Object>> ventasPedidos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String momentOfDay,
            @RequestParam(required = false) String dayOfWeek,
            @RequestParam(required = false) String weatherCondition
    ) {
        LocalDateTime f0 = fromDef(from);
        LocalDateTime t0 = toDefExclusive(to);
        return ResponseEntity.ok(adminDashboardService.ventasPedidos(f0, t0, status, momentOfDay, dayOfWeek, weatherCondition));
    }

    @GetMapping("/inventario-costos")
    public ResponseEntity<Map<String, Object>> inventarioCostos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String categoriaInsumo,
            @RequestParam(required = false) String tipoMovimiento,
            @RequestParam(required = false, defaultValue = "false") boolean soloStockBajo,
            @RequestParam(required = false, defaultValue = "10") double umbralStockBajo
    ) {
        LocalDateTime f0 = fromDef(from);
        LocalDateTime t0 = toDefExclusive(to);
        return ResponseEntity.ok(adminDashboardService.inventarioCostos(f0, t0, categoriaInsumo, tipoMovimiento, soloStockBajo, umbralStockBajo));
    }

    @GetMapping("/productos")
    public ResponseEntity<Map<String, Object>> productos(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String categoriaProducto,
            @RequestParam(required = false) Integer estrellasMin,
            @RequestParam(required = false) Double precioMin,
            @RequestParam(required = false) Double precioMax
    ) {
        LocalDateTime f0 = fromDef(from);
        LocalDateTime t0 = toDefExclusive(to);
        return ResponseEntity.ok(adminDashboardService.productos(f0, t0, categoriaProducto, estrellasMin, precioMin, precioMax));
    }

    @GetMapping("/clientes")
    public ResponseEntity<Map<String, Object>> clientes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime regFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime regTo,
            @RequestParam(required = false) Integer estrellasFiltro,
            @RequestParam(required = false) Boolean soloRecurrentes
    ) {
        LocalDateTime f0 = fromDef(from);
        LocalDateTime t0 = toDefExclusive(to);
        LocalDateTime regToEx = regTo != null ? regTo : t0;
        return ResponseEntity.ok(adminDashboardService.clientes(f0, t0, regFrom, regToEx, estrellasFiltro, soloRecurrentes));
    }

    @GetMapping("/operacion")
    public ResponseEntity<Map<String, Object>> operacion(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) UUID cajeroId,
            @RequestParam(required = false) UUID repartidorId
    ) {
        LocalDateTime f0 = fromDef(from);
        LocalDateTime t0 = toDefExclusive(to);
        return ResponseEntity.ok(adminDashboardService.operacion(f0, t0, cajeroId, repartidorId));
    }

    @GetMapping("/seguridad")
    public ResponseEntity<Map<String, Object>> seguridad(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String rol
    ) {
        LocalDateTime f0 = fromDef(from);
        LocalDateTime t0 = toDefExclusive(to);
        return ResponseEntity.ok(adminDashboardService.seguridad(f0, t0, status, rol));
    }

    @GetMapping("/interacciones")
    public ResponseEntity<Map<String, Object>> interacciones(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String condicionClima,
            @RequestParam(required = false) String segmento,
            @RequestParam(required = false) String userId
    ) {
        LocalDateTime f0 = fromDef(from);
        LocalDateTime t0 = toDefExclusive(to);
        return ResponseEntity.ok(adminDashboardService.interacciones(f0, t0, action, condicionClima, segmento, userId));
    }
}
