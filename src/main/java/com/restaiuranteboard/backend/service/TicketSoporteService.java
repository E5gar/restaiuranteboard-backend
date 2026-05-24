package com.restaiuranteboard.backend.service;

import com.restaiuranteboard.backend.exception.EmailDispatchException;
import com.restaiuranteboard.backend.model.nosql.TicketSoporte;
import com.restaiuranteboard.backend.model.nosql.TicketSoporteCounter;
import com.restaiuranteboard.backend.model.sql.RestaurantOrder;
import com.restaiuranteboard.backend.model.sql.User;
import com.restaiuranteboard.backend.repository.nosql.TicketSoporteCounterRepository;
import com.restaiuranteboard.backend.repository.nosql.TicketSoporteRepository;
import com.restaiuranteboard.backend.repository.sql.RestaurantOrderRepository;
import com.restaiuranteboard.backend.repository.sql.UserRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class TicketSoporteService {

    private static final ZoneId ZONA = ZoneId.of("America/Lima");
    private static final int MAX_DESC = 500;
    private static final long MAX_EVIDENCIA_BYTES = 5L * 1024 * 1024;
    private static final Set<String> MIME_OK = Set.of("image/jpeg", "image/jpg", "image/png");

    private final TicketSoporteRepository ticketSoporteRepository;
    private final TicketSoporteCounterRepository counterRepository;
    private final RestaurantOrderRepository orderRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final SimpMessagingTemplate messagingTemplate;

    public TicketSoporteService(
            TicketSoporteRepository ticketSoporteRepository,
            TicketSoporteCounterRepository counterRepository,
            RestaurantOrderRepository orderRepository,
            UserRepository userRepository,
            EmailService emailService,
            SimpMessagingTemplate messagingTemplate
    ) {
        this.ticketSoporteRepository = ticketSoporteRepository;
        this.counterRepository = counterRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.messagingTemplate = messagingTemplate;
    }

    public List<Map<String, Object>> pedidosRecientes48h(UUID clientId) {
        User u = requerirCliente(clientId);
        LocalDateTime since = LocalDateTime.now(ZONA).minusHours(48);
        List<RestaurantOrder> orders = orderRepository.findByClient_IdAndCreatedAtAfterOrderByCreatedAtDesc(
                u.getId(),
                since
        );
        List<Map<String, Object>> out = new ArrayList<>();
        for (RestaurantOrder o : orders) {
            Map<String, Object> row = new LinkedHashMap<>();
            String idShort = o.getId() == null ? "" : o.getId().toString().replace("-", "");
            if (idShort.length() > 6) {
                idShort = idShort.substring(0, 6);
            }
            row.put("orderId", o.getId() == null ? "" : o.getId().toString());
            row.put("label", "#" + idShort + " | "
                    + formatearFecha(o.getCreatedAt()) + " | S/ "
                    + (o.getTotalPrice() == null ? "0.00" : o.getTotalPrice().setScale(2, RoundingMode.HALF_UP))
                    + " | " + (o.getStatus() == null ? "" : o.getStatus()));
            row.put("status", o.getStatus());
            row.put("total", o.getTotalPrice());
            row.put("createdAt", o.getCreatedAt() == null ? null : o.getCreatedAt().toString());
            out.add(row);
        }
        return out;
    }

    public Map<String, Object> crearTicket(
            UUID clientId,
            String orderId,
            String categoria,
            String descripcion,
            MultipartFile evidencia
    ) {
        User cliente = requerirCliente(clientId);
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("Selecciona un pedido.");
        }
        String cat = normalizarCategoria(categoria);
        if (cat == null) {
            throw new IllegalArgumentException("Categoria invalida.");
        }
        String desc = descripcion == null ? "" : descripcion.trim();
        if (desc.isBlank()) {
            throw new IllegalArgumentException("Describe el problema.");
        }
        if (desc.length() > MAX_DESC) {
            throw new IllegalArgumentException("La descripcion no puede superar 500 caracteres.");
        }
        UUID oid = UUID.fromString(orderId.trim());
        RestaurantOrder order = orderRepository.findById(oid)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado."));
        if (order.getClient() == null || !cliente.getId().equals(order.getClient().getId())) {
            throw new IllegalArgumentException("El pedido no pertenece a tu cuenta.");
        }
        LocalDateTime since = LocalDateTime.now(ZONA).minusHours(48);
        if (order.getCreatedAt() == null || order.getCreatedAt().isBefore(since)) {
            throw new IllegalArgumentException("Solo puedes reportar pedidos de las ultimas 48 horas.");
        }

        String evidenciaB64 = null;
        String evidenciaMime = null;
        if (evidencia != null && !evidencia.isEmpty()) {
            String mime = evidencia.getContentType() == null ? "" : evidencia.getContentType().toLowerCase(Locale.ROOT);
            if (!MIME_OK.contains(mime)) {
                throw new IllegalArgumentException("La evidencia debe ser JPG o PNG.");
            }
            if (evidencia.getSize() > MAX_EVIDENCIA_BYTES) {
                throw new IllegalArgumentException("La imagen no debe superar 5MB.");
            }
            try {
                evidenciaB64 = Base64.getEncoder().encodeToString(evidencia.getBytes());
                evidenciaMime = mime;
            } catch (Exception e) {
                throw new IllegalArgumentException("No se pudo leer la imagen.");
            }
        }

        TicketSoporte ticket = new TicketSoporte();
        ticket.setTicketCode(siguienteCodigo());
        ticket.setClientUserId(cliente.getId().toString());
        ticket.setClientEmail(cliente.getEmail());
        ticket.setClientName(cliente.getFullName());
        ticket.setOrderId(order.getId().toString());
        ticket.setOrderResumen(resumenPedido(order));
        ticket.setCategoria(cat);
        ticket.setDescripcion(desc);
        ticket.setEvidenciaBase64(evidenciaB64);
        ticket.setEvidenciaMime(evidenciaMime);
        ticket.setStatus("PENDIENTE");
        ticket.setCreatedAt(Instant.now());
        ticket.setUpdatedAt(Instant.now());
        ticketSoporteRepository.save(ticket);

        try {
            emailService.enviarConfirmacionTicketSoporte(
                    cliente.getEmail(),
                    ticket.getTicketCode(),
                    etiquetaCategoria(cat),
                    cliente.getId().toString()
            );
        } catch (EmailDispatchException ignored) {
        }

        messagingTemplate.convertAndSend("/topic/admin/soporte", "ticket_nuevo");
        long pendientes = ticketSoporteRepository.countByStatus("PENDIENTE");
        messagingTemplate.convertAndSend("/topic/admin/soporte", "{\"event\":\"ticket_nuevo\",\"pendientes\":" + pendientes + "}");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ticketId", ticket.getId());
        body.put("ticketCode", ticket.getTicketCode());
        body.put("status", ticket.getStatus());
        return body;
    }

    public long contarPendientes() {
        return ticketSoporteRepository.countByStatus("PENDIENTE");
    }

    public List<Map<String, Object>> listarTicketsAdmin() {
        List<TicketSoporte> rows = ticketSoporteRepository.findAllByOrderByCreatedAtDesc();
        List<Map<String, Object>> out = new ArrayList<>();
        for (TicketSoporte t : rows) {
            out.add(toAdminDto(t));
        }
        return out;
    }

    public Map<String, Object> cerrarTicket(String ticketId, String adminEmail, String mensajeCierre) {
        if (mensajeCierre == null || mensajeCierre.isBlank()) {
            throw new IllegalArgumentException("Indica la justificacion del cierre.");
        }
        String msg = mensajeCierre.trim();
        if (msg.length() > 1000) {
            throw new IllegalArgumentException("La justificacion no puede superar 1000 caracteres.");
        }
        TicketSoporte t = ticketSoporteRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket no encontrado."));
        if (!"PENDIENTE".equalsIgnoreCase(t.getStatus())) {
            throw new IllegalArgumentException("El ticket ya esta cerrado.");
        }
        t.setStatus("CERRADO");
        t.setCierreMensaje(msg);
        t.setCerradoPorEmail(adminEmail);
        t.setClosedAt(Instant.now());
        t.setUpdatedAt(Instant.now());
        ticketSoporteRepository.save(t);

        if (t.getClientEmail() != null && !t.getClientEmail().isBlank()) {
            try {
                emailService.enviarCierreTicketSoporte(
                        t.getClientEmail(),
                        t.getTicketCode(),
                        msg,
                        t.getClientUserId()
                );
            } catch (EmailDispatchException ignored) {
            }
        }

        long pendientes = ticketSoporteRepository.countByStatus("PENDIENTE");
        messagingTemplate.convertAndSend("/topic/admin/soporte", "{\"event\":\"ticket_cerrado\",\"pendientes\":" + pendientes + "}");

        return toAdminDto(t);
    }

    private synchronized String siguienteCodigo() {
        TicketSoporteCounter counter = counterRepository.findById("TIAT")
                .orElseGet(() -> {
                    TicketSoporteCounter c = new TicketSoporteCounter();
                    c.setId("TIAT");
                    c.setSeq(0L);
                    return counterRepository.save(c);
                });
        counter.setSeq(counter.getSeq() + 1);
        counterRepository.save(counter);
        return String.format("#TIAT-%05d", counter.getSeq());
    }

    private User requerirCliente(UUID clientId) {
        if (clientId == null) {
            throw new IllegalArgumentException("Usuario requerido.");
        }
        User u = userRepository.findById(clientId).orElseThrow(
                () -> new IllegalArgumentException("Usuario no encontrado.")
        );
        if (u.isDeleted() || u.getRole() == null || !"CLIENTE".equalsIgnoreCase(u.getRole().getName())) {
            throw new IllegalArgumentException("No autorizado.");
        }
        return u;
    }

    private static String resumenPedido(RestaurantOrder o) {
        String idShort = o.getId() == null ? "" : o.getId().toString().replace("-", "");
        if (idShort.length() > 6) {
            idShort = idShort.substring(0, 6);
        }
        return "#" + idShort + " | " + formatearFecha(o.getCreatedAt()) + " | " + (o.getStatus() == null ? "" : o.getStatus());
    }

    private static String formatearFecha(LocalDateTime dt) {
        if (dt == null) {
            return "";
        }
        return dt.toString().replace('T', ' ').substring(0, 16);
    }

    private Map<String, Object> toAdminDto(TicketSoporte t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("ticketCode", t.getTicketCode());
        m.put("clientName", t.getClientName());
        m.put("clientEmail", t.getClientEmail());
        m.put("orderId", t.getOrderId());
        m.put("orderResumen", t.getOrderResumen());
        m.put("categoria", t.getCategoria());
        m.put("categoriaLabel", etiquetaCategoria(t.getCategoria()));
        m.put("descripcion", t.getDescripcion());
        m.put("tieneEvidencia", t.getEvidenciaBase64() != null && !t.getEvidenciaBase64().isBlank());
        m.put("status", t.getStatus());
        m.put("cierreMensaje", t.getCierreMensaje());
        m.put("cerradoPorEmail", t.getCerradoPorEmail());
        m.put("createdAt", t.getCreatedAt() == null ? null : t.getCreatedAt().toString());
        m.put("closedAt", t.getClosedAt() == null ? null : t.getClosedAt().toString());
        return m;
    }

    public Map<String, Object> evidenciaDataUrl(String ticketId) {
        TicketSoporte t = ticketSoporteRepository.findById(ticketId)
                .orElseThrow(() -> new IllegalArgumentException("Ticket no encontrado."));
        if (t.getEvidenciaBase64() == null || t.getEvidenciaBase64().isBlank()) {
            throw new IllegalArgumentException("Sin evidencia.");
        }
        String mime = t.getEvidenciaMime() == null || t.getEvidenciaMime().isBlank()
                ? "image/jpeg" : t.getEvidenciaMime();
        return Map.of("dataUrl", "data:" + mime + ";base64," + t.getEvidenciaBase64());
    }

    private static String normalizarCategoria(String raw) {
        if (raw == null) return null;
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "PEDIDO_INCOMPLETO" -> "PEDIDO_INCOMPLETO";
            case "PEDIDO_INCORRECTO" -> "PEDIDO_INCORRECTO";
            case "PEDIDO_MAL_ESTADO" -> "PEDIDO_MAL_ESTADO";
            case "RETRASO_ENTREGA" -> "RETRASO_ENTREGA";
            case "INCONVENIENTE_REPARTIDOR" -> "INCONVENIENTE_REPARTIDOR";
            case "ERROR_PAGO" -> "ERROR_PAGO";
            default -> null;
        };
    }

    public static String etiquetaCategoria(String cat) {
        if (cat == null) return "";
        return switch (cat) {
            case "PEDIDO_INCOMPLETO" -> "Pedido incompleto";
            case "PEDIDO_INCORRECTO" -> "Pedido incorrecto";
            case "PEDIDO_MAL_ESTADO" -> "Pedido en mal estado";
            case "RETRASO_ENTREGA" -> "Retraso con la entrega";
            case "INCONVENIENTE_REPARTIDOR" -> "Inconveniente con el repartidor";
            case "ERROR_PAGO" -> "Error en el pago";
            default -> cat;
        };
    }
}
