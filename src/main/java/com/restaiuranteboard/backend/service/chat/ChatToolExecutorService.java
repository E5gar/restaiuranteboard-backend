package com.restaiuranteboard.backend.service.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaiuranteboard.backend.dto.CarritoResponse;
import com.restaiuranteboard.backend.dto.SeguimientoPedidoResponse;
import com.restaiuranteboard.backend.model.nosql.ConfiguracionSistema;
import com.restaiuranteboard.backend.model.nosql.Producto;
import com.restaiuranteboard.backend.model.sql.EmailComunicacionPersonal;
import com.restaiuranteboard.backend.model.sql.Inventory;
import com.restaiuranteboard.backend.model.sql.User;
import com.restaiuranteboard.backend.repository.nosql.ConfiguracionSistemaRepository;
import com.restaiuranteboard.backend.repository.nosql.ProductoRepository;
import com.restaiuranteboard.backend.repository.sql.EmailComunicacionPersonalRepository;
import com.restaiuranteboard.backend.repository.sql.InventoryRepository;
import com.restaiuranteboard.backend.repository.sql.UserRepository;
import com.restaiuranteboard.backend.service.EmailService;
import com.restaiuranteboard.backend.service.ShoppingCartService;
import com.restaiuranteboard.backend.service.SeguimientoPedidoService;
import com.restaiuranteboard.backend.service.dashboard.AdminDashboardService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatToolExecutorService {

    private static final int MAX_QTY = 10;

    private final ObjectMapper objectMapper;
    private final ProductoRepository productoRepository;
    private final ShoppingCartService shoppingCartService;
    private final SeguimientoPedidoService seguimientoPedidoService;
    private final AdminDashboardService adminDashboardService;
    private final InventoryRepository inventoryRepository;
    private final UserRepository userRepository;
    private final ConfiguracionSistemaRepository configRepository;
    private final EmailComunicacionPersonalRepository emailComunicacionPersonalRepository;
    private final EmailService emailService;
    private final CartChatNotifyService cartChatNotifyService;

    public ChatToolExecutorService(
            ObjectMapper objectMapper,
            ProductoRepository productoRepository,
            ShoppingCartService shoppingCartService,
            SeguimientoPedidoService seguimientoPedidoService,
            AdminDashboardService adminDashboardService,
            InventoryRepository inventoryRepository,
            UserRepository userRepository,
            ConfiguracionSistemaRepository configRepository,
            EmailComunicacionPersonalRepository emailComunicacionPersonalRepository,
            EmailService emailService,
            CartChatNotifyService cartChatNotifyService
    ) {
        this.objectMapper = objectMapper;
        this.productoRepository = productoRepository;
        this.shoppingCartService = shoppingCartService;
        this.seguimientoPedidoService = seguimientoPedidoService;
        this.adminDashboardService = adminDashboardService;
        this.inventoryRepository = inventoryRepository;
        this.userRepository = userRepository;
        this.configRepository = configRepository;
        this.emailComunicacionPersonalRepository = emailComunicacionPersonalRepository;
        this.emailService = emailService;
        this.cartChatNotifyService = cartChatNotifyService;
    }

    public String ejecutar(String toolName, String argsJson, User actor, boolean adminChat) {
        try {
            JsonNode args = objectMapper.readTree(argsJson == null || argsJson.isBlank() ? "{}" : argsJson);
            return switch (toolName) {
                case "buscar_productos" -> buscarProductos(args);
                case "modificar_carrito" -> modificarCarrito(args, actor);
                case "consultar_estado_pedido" -> consultarEstadoPedido(actor);
                case "obtener_stock_ingrediente" -> obtenerStockIngrediente(args);
                case "obtener_kpis_ventas" -> obtenerKpisVentas(args);
                case "obtener_alertas_inventario" -> obtenerAlertasInventario();
                case "listar_personal" -> listarPersonal(args);
                case "enviar_email_personal" -> enviarEmailPersonal(args, actor);
                default -> "{\"ok\":false,\"error\":\"herramienta_desconocida\"}";
            };
        } catch (Exception e) {
            return "{\"ok\":false,\"error\":\"" + esc(e.getMessage()) + "\"}";
        }
    }

    private String buscarProductos(JsonNode args) {
        String categoria = txt(args, "categoria");
        Double precioMax = dbl(args, "precio_max");
        String orden = txt(args, "orden_por_precio");
        String texto = txt(args, "texto");
        List<Producto> rows = productoRepository.findByIsDeletedFalse();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Producto p : rows) {
            if (!categoria.isBlank() && !eqCat(p.getCategory(), categoria)) continue;
            if (precioMax != null && p.getPrice() != null && p.getPrice() > precioMax) continue;
            if (!texto.isBlank()) {
                String blob = (p.getName() + " " + p.getCategory()).toLowerCase(Locale.ROOT);
                if (!blob.contains(texto.toLowerCase(Locale.ROOT))) continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("nombre", p.getName());
            m.put("categoria", p.getCategory());
            m.put("precio", p.getPrice());
            out.add(m);
        }
        if ("asc".equalsIgnoreCase(orden)) {
            out.sort(Comparator.comparingDouble(x -> ((Number) x.get("precio")).doubleValue()));
        } else if ("desc".equalsIgnoreCase(orden) || orden.isBlank()) {
            out.sort(Comparator.comparingDouble((Map<String, Object> x) -> ((Number) x.get("precio")).doubleValue()).reversed());
        }
        if (out.size() > 12) {
            out = out.subList(0, 12);
        }
        return json(Map.of("ok", true, "productos", out));
    }

    private String modificarCarrito(JsonNode args, User actor) {
        if (actor == null || actor.getId() == null) {
            return "{\"ok\":false,\"error\":\"usuario_invalido\"}";
        }
        String userId = actor.getId().toString();
        String productoId = txt(args, "producto_id");
        String nombre = txt(args, "nombre");
        int cantidad = Math.max(1, args.path("cantidad").asInt(1));
        String accion = txt(args, "accion").toUpperCase(Locale.ROOT);
        if (productoId.isBlank() && !nombre.isBlank()) {
            productoId = resolverProductoIdPorNombre(nombre);
        }
        if (productoId.isBlank()) {
            return "{\"ok\":false,\"error\":\"producto_no_encontrado\"}";
        }
        final String pid = productoId;
        CarritoResponse cart = shoppingCartService.obtenerCarrito(userId);
        switch (accion) {
            case "AGREGAR" -> {
                int actual = cart.items().stream()
                        .filter(l -> pid.equals(l.productId()))
                        .mapToInt(l -> l.quantity())
                        .findFirst()
                        .orElse(0);
                if (actual + cantidad > MAX_QTY) {
                    return "{\"ok\":false,\"error\":\"maximo_10_unidades\"}";
                }
                for (int i = 0; i < cantidad; i++) {
                    cart = shoppingCartService.agregarUnidad(userId, pid);
                }
            }
            case "QUITAR" -> {
                for (int i = 0; i < cantidad; i++) {
                    cart = shoppingCartService.decrementar(userId, pid);
                }
            }
            case "ELIMINAR_LINEA" -> cart = shoppingCartService.eliminarLinea(userId, pid);
            default -> {
                return "{\"ok\":false,\"error\":\"accion_invalida\"}";
            }
        }
        cartChatNotifyService.notificarCarritoActualizado(userId);
        double total = cart.items().stream().mapToDouble(l -> l.unitPrice() * l.quantity()).sum();
        return json(Map.of(
                "ok", true,
                "total_carrito", total,
                "lineas", cart.items().size()
        ));
    }

    private String consultarEstadoPedido(User actor) {
        if (actor == null || actor.getId() == null) {
            return "{\"ok\":false,\"error\":\"usuario_invalido\"}";
        }
        try {
            SeguimientoPedidoResponse p = seguimientoPedidoService.obtenerPedidoActual(actor.getId());
            return json(Map.of(
                    "ok", true,
                    "estado", p.estado(),
                    "total", p.total(),
                    "repartidor", p.repartidorNombre() == null ? "" : p.repartidorNombre()
            ));
        } catch (Exception e) {
            return json(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    private String obtenerStockIngrediente(JsonNode args) {
        String idRaw = txt(args, "id_insumo");
        if (idRaw.isBlank()) {
            return "{\"ok\":false,\"error\":\"id_requerido\"}";
        }
        try {
            int id = Integer.parseInt(idRaw);
            Inventory ing = inventoryRepository.findByIdAndIsDeletedFalse(id).orElse(null);
            if (ing == null) {
                return "{\"ok\":false,\"error\":\"insumo_no_encontrado\"}";
            }
            return json(Map.of(
                    "ok", true,
                    "id", ing.getId(),
                    "nombre", ing.getName(),
                    "stock", ing.getStockQuantity(),
                    "precio", ing.getPrice()
            ));
        } catch (NumberFormatException e) {
            return "{\"ok\":false,\"error\":\"id_invalido\"}";
        }
    }

    private String obtenerKpisVentas(JsonNode args) {
        LocalDate fin = LocalDate.now();
        LocalDate ini = fin.minusDays(30);
        String fi = txt(args, "fecha_inicio");
        String ff = txt(args, "fecha_fin");
        if (!fi.isBlank()) {
            try {
                ini = LocalDate.parse(fi);
            } catch (Exception ignored) {
            }
        }
        if (!ff.isBlank()) {
            try {
                fin = LocalDate.parse(ff);
            } catch (Exception ignored) {
            }
        }
        LocalDateTime from = ini.atStartOfDay();
        LocalDateTime toEx = fin.plusDays(1).atStartOfDay();
        Map<String, Object> data = adminDashboardService.ventasPedidos(from, toEx, null, null, null, null);
        Object kpis = data.get("kpis");
        return json(Map.of("ok", true, "kpis", kpis == null ? Map.of() : kpis));
    }

    private String obtenerAlertasInventario() {
        Map<String, Object> data = adminDashboardService.inventarioCostos(
                LocalDate.now().minusDays(30).atStartOfDay(),
                LocalDate.now().plusDays(1).atStartOfDay(),
                null,
                null,
                true,
                10d
        );
        Object kpis = data.get("kpis");
        Object tablas = data.get("tablas");
        return json(Map.of("ok", true, "kpis", kpis == null ? Map.of() : kpis, "tablas", tablas == null ? Map.of() : tablas));
    }

    private String listarPersonal(JsonNode args) {
        String rol = txt(args, "rol").toUpperCase(Locale.ROOT);
        List<String> roles = rol.isBlank()
                ? List.of("CAJERO", "COCINERO", "REPARTIDOR")
                : List.of(rol);
        List<Map<String, String>> out = new ArrayList<>();
        for (String r : roles) {
            for (User u : userRepository.findByRole_NameAndIsDeletedFalse(r)) {
                Map<String, String> m = new LinkedHashMap<>();
                m.put("rol", r);
                m.put("nombre", u.getFullName());
                m.put("email", u.getEmail());
                out.add(m);
            }
        }
        return json(Map.of("ok", true, "personal", out));
    }

    private String enviarEmailPersonal(JsonNode args, User admin) {
        if (admin == null || admin.getEmail() == null) {
            return "{\"ok\":false,\"error\":\"admin_invalido\"}";
        }
        String destino = txt(args, "email_destino");
        String nombre = txt(args, "nombre_destino");
        String mensaje = txt(args, "mensaje");
        if (mensaje.isBlank()) {
            return "{\"ok\":false,\"error\":\"mensaje_vacio\"}";
        }
        User dest = null;
        if (!destino.isBlank()) {
            dest = userRepository.findByEmailIgnoreCase(destino.trim()).orElse(null);
        } else if (!nombre.isBlank()) {
            dest = userRepository.findAll().stream()
                    .filter(u -> !u.isDeleted())
                    .filter(u -> u.getFullName() != null
                            && u.getFullName().toLowerCase(Locale.ROOT).contains(nombre.toLowerCase(Locale.ROOT)))
                    .findFirst()
                    .orElse(null);
        }
        if (dest == null || dest.getEmail() == null || dest.getEmail().isBlank()) {
            return "{\"ok\":false,\"error\":\"destinatario_no_encontrado\"}";
        }
        ConfiguracionSistema cfg = configRepository.findById("GLOBAL_CONFIG").orElse(null);
        if (cfg == null || cfg.getEmailSmtp() == null || cfg.getPasswordSmtp() == null || cfg.getPasswordSmtp().isBlank()) {
            return "{\"ok\":false,\"error\":\"smtp_no_configurado\"}";
        }
        String negocio = cfg.getNombreNegocio() == null || cfg.getNombreNegocio().isBlank()
                ? "Restaiuranteboard" : cfg.getNombreNegocio().trim();
        String subject = "Mensaje de Administrador de " + negocio;
        EmailComunicacionPersonal row = new EmailComunicacionPersonal();
        row.setAdminEmail(admin.getEmail());
        row.setRecipientEmail(dest.getEmail());
        row.setContenido(mensaje);
        emailComunicacionPersonalRepository.save(row);
        emailService.enviarCorreoTextoPlano(
                dest.getEmail(),
                subject,
                mensaje,
                cfg.getEmailSmtp(),
                cfg.getPasswordSmtp(),
                admin.getId() == null ? null : admin.getId().toString()
        );
        return json(Map.of("ok", true, "enviado_a", dest.getEmail()));
    }

    private String resolverProductoIdPorNombre(String nombre) {
        String q = nombre.toLowerCase(Locale.ROOT);
        return productoRepository.findByIsDeletedFalse().stream()
                .filter(p -> p.getName() != null && p.getName().toLowerCase(Locale.ROOT).contains(q))
                .map(Producto::getId)
                .findFirst()
                .orElse("");
    }

    private static boolean eqCat(String a, String b) {
        if (a == null || b == null) return false;
        return a.trim().equalsIgnoreCase(b.trim());
    }

    private static String txt(JsonNode n, String f) {
        return n.path(f).asText("").trim();
    }

    private static Double dbl(JsonNode n, String f) {
        if (!n.has(f) || n.get(f).isNull()) return null;
        return n.get(f).asDouble();
    }

    private String json(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return "{\"ok\":false}";
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
