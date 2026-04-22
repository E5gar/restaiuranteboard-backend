package com.restaiuranteboard.backend.service;

import com.restaiuranteboard.backend.dto.CajaLineaDetalle;
import com.restaiuranteboard.backend.dto.SeguimientoPedidoResponse;
import com.restaiuranteboard.backend.model.nosql.Producto;
import com.restaiuranteboard.backend.model.sql.OrderItem;
import com.restaiuranteboard.backend.model.sql.RestaurantOrder;
import com.restaiuranteboard.backend.model.sql.User;
import com.restaiuranteboard.backend.repository.nosql.ProductoRepository;
import com.restaiuranteboard.backend.repository.sql.OrderItemRepository;
import com.restaiuranteboard.backend.repository.sql.RestaurantOrderRepository;
import com.restaiuranteboard.backend.repository.sql.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class SeguimientoPedidoService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RestaurantOrderRepository orderRepository;
    @Autowired
    private OrderItemRepository orderItemRepository;
    @Autowired
    private ProductoRepository productoRepository;

    @Transactional(readOnly = true)
    public SeguimientoPedidoResponse obtenerPedidoActual(UUID userId) {
        User u = requerirCliente(userId);
        List<RestaurantOrder> activos = orderRepository.findByClient_IdAndStatusNotInOrderByCreatedAtDesc(
                u.getId(), List.of("ENTREGADO", "CANCELADO"));
        RestaurantOrder order = null;
        if (!activos.isEmpty()) {
            order = activos.get(0);
        } else {
            List<RestaurantOrder> all = orderRepository.findByClient_IdOrderByCreatedAtDesc(u.getId());
            if (!all.isEmpty()) {
                order = all.get(0);
            }
        }
        if (order == null) {
            throw new IllegalArgumentException("No tienes pedidos registrados.");
        }
        List<CajaLineaDetalle> lineas = mapLineas(orderItemRepository.findByRestaurantOrder_Id(order.getId()));
        String repartidor = "";
        if (order.getDeliveryPerson() != null && order.getDeliveryPerson().getFullName() != null) {
            repartidor = order.getDeliveryPerson().getFullName().trim();
        }
        return new SeguimientoPedidoResponse(
                order.getId().toString(),
                order.getStatus(),
                order.getCreatedAt() == null ? "" : DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(order.getCreatedAt()),
                order.getTotalPrice() == null ? "0.00" : order.getTotalPrice().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                order.getCancelReason() == null ? "" : order.getCancelReason(),
                repartidor,
                lineas
        );
    }

    private User requerirCliente(UUID userId) {
        if (userId == null) throw new IllegalArgumentException("Usuario requerido.");
        User u = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        if (u.isDeleted() || u.getRole() == null) throw new IllegalArgumentException("No autorizado.");
        if (!Set.of("CLIENTE", "ADMIN").contains(u.getRole().getName())) throw new IllegalArgumentException("No autorizado.");
        return u;
    }

    private List<CajaLineaDetalle> mapLineas(List<OrderItem> items) {
        List<CajaLineaDetalle> out = new ArrayList<>();
        for (OrderItem oi : items) {
            Producto p = productoRepository.findById(oi.getMongoProductId()).orElse(null);
            String nombre = p != null && p.getName() != null ? p.getName() : "Producto";
            int qty = oi.getQuantity() == null ? 0 : oi.getQuantity();
            BigDecimal unit = oi.getPriceAtMoment() == null ? BigDecimal.ZERO : oi.getPriceAtMoment().setScale(2, RoundingMode.HALF_UP);
            BigDecimal sub = unit.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
            out.add(new CajaLineaDetalle(nombre, qty, unit.toPlainString(), sub.toPlainString()));
        }
        return out;
    }
}
