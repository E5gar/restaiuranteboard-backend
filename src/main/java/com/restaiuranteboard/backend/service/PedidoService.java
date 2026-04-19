package com.restaiuranteboard.backend.service;

import com.restaiuranteboard.backend.model.nosql.CartItemMongo;
import com.restaiuranteboard.backend.model.nosql.Producto;
import com.restaiuranteboard.backend.model.nosql.ShoppingCart;
import com.restaiuranteboard.backend.model.sql.OrderItem;
import com.restaiuranteboard.backend.model.sql.RestaurantOrder;
import com.restaiuranteboard.backend.model.sql.User;
import com.restaiuranteboard.backend.repository.nosql.ProductoRepository;
import com.restaiuranteboard.backend.repository.nosql.ShoppingCartRepository;
import com.restaiuranteboard.backend.repository.sql.OrderItemRepository;
import com.restaiuranteboard.backend.repository.sql.RestaurantOrderRepository;
import com.restaiuranteboard.backend.repository.sql.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class PedidoService {

    private static final long MAX_BYTES_COMPROBANTE = 3L * 1024 * 1024;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private ShoppingCartRepository shoppingCartRepository;

    @Autowired
    private ShoppingCartService shoppingCartService;

    @Autowired
    private RestaurantOrderRepository restaurantOrderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Transactional
    public Map<String, Object> crearPedidoConComprobante(UUID clientId, byte[] comprobanteBytes, String contentType) {
        validarComprobante(comprobanteBytes, contentType);

        User client = userRepository.findById(clientId).orElseThrow(
                () -> new IllegalArgumentException("Usuario no encontrado."));
        if (client.isDeleted()) {
            throw new IllegalArgumentException("Usuario no encontrado.");
        }
        if (client.getRole() == null || !"CLIENTE".equals(client.getRole().getName())) {
            throw new IllegalArgumentException("Solo los clientes pueden crear pedidos.");
        }

        String userIdStr = clientId.toString();
        ShoppingCart cart = shoppingCartService.getOrCreate(userIdStr);
        shoppingCartService.sanitizeAndPersist(cart);

        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new IllegalArgumentException("Tu carrito está vacío o los productos ya no están disponibles.");
        }

        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        List<OrderItem> lineasPendientes = new ArrayList<>();

        for (CartItemMongo line : cart.getItems()) {
            Producto p = productoRepository.findById(line.getProductId()).orElse(null);
            if (p == null || p.isDeleted()) {
                continue;
            }
            BigDecimal unit = BigDecimal.valueOf(safePrice(p.getPrice())).setScale(2, RoundingMode.HALF_UP);
            int qty = line.getQuantity();
            BigDecimal sub = unit.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
            total = total.add(sub);

            OrderItem oi = new OrderItem();
            oi.setMongoProductId(line.getProductId());
            oi.setQuantity(qty);
            oi.setPriceAtMoment(unit);
            lineasPendientes.add(oi);
        }

        if (lineasPendientes.isEmpty()) {
            throw new IllegalArgumentException("Tu carrito está vacío o los productos ya no están disponibles.");
        }

        RestaurantOrder order = new RestaurantOrder();
        order.setClient(client);
        order.setStatus("VALIDANDO_PAGO");
        order.setTotalPrice(total);
        order.setPaymentReceiptImage(comprobanteBytes);
        RestaurantOrder guardado = restaurantOrderRepository.save(order);

        for (OrderItem oi : lineasPendientes) {
            oi.setRestaurantOrder(guardado);
            orderItemRepository.save(oi);
        }

        cart.getItems().clear();
        shoppingCartRepository.save(cart);

        return Map.of(
                "orderId", guardado.getId().toString(),
                "total", total
        );
    }

    private void validarComprobante(byte[] data, String contentType) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Debes adjuntar el comprobante de pago.");
        }
        if (data.length > MAX_BYTES_COMPROBANTE) {
            throw new IllegalArgumentException("La imagen no debe pesar más de 3MB.");
        }
        String ct = contentType != null ? contentType.toLowerCase(Locale.ROOT) : "";
        boolean ok = ct.contains("jpeg") || ct.contains("jpg") || ct.contains("png");
        if (!ok) {
            throw new IllegalArgumentException("Solo se permiten imágenes en formato JPG o PNG.");
        }
    }

    private double safePrice(Double price) {
        return price == null ? 0.0 : price;
    }
}
