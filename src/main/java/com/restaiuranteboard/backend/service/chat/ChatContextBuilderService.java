package com.restaiuranteboard.backend.service.chat;

import com.restaiuranteboard.backend.dto.CarritoLineaResponse;
import com.restaiuranteboard.backend.dto.CarritoResponse;
import com.restaiuranteboard.backend.model.sql.User;
import com.restaiuranteboard.backend.service.ContextoInteligenciaService;
import com.restaiuranteboard.backend.service.ShoppingCartService;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ChatContextBuilderService {

    private static final ZoneId ZONA = ZoneId.of("America/Lima");

    private final ContextoInteligenciaService contextoInteligenciaService;
    private final ShoppingCartService shoppingCartService;

    public ChatContextBuilderService(
            ContextoInteligenciaService contextoInteligenciaService,
            ShoppingCartService shoppingCartService
    ) {
        this.contextoInteligenciaService = contextoInteligenciaService;
        this.shoppingCartService = shoppingCartService;
    }

    public String bloqueContexto(User user, boolean admin) {
        StringBuilder sb = new StringBuilder();
        sb.append("CTX ");
        if (user != null) {
            sb.append("user=").append(user.getFullName()).append("|rol=").append(role(user));
            sb.append("|id=").append(user.getId());
            if (!admin && user.getAddress() != null && !user.getAddress().isBlank()) {
                sb.append("|dir=").append(user.getAddress().trim());
            }
        }
        ZonedDateTime now = ZonedDateTime.now(ZONA);
        sb.append("|fecha=").append(now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        var amb = contextoInteligenciaService.contextoActual();
        sb.append("|clima=").append(amb.condition()).append("|temp=").append(amb.temp());
        sb.append("|segmento=").append(amb.segment()).append("|dia=").append(amb.day());
        if (!admin && user != null && user.getId() != null) {
            try {
                CarritoResponse cart = shoppingCartService.obtenerCarrito(user.getId().toString());
                String lines = cart.items().stream()
                        .map(l -> l.name() + "x" + l.quantity())
                        .collect(Collectors.joining(","));
                sb.append("|carrito=").append(lines.isBlank() ? "vacio" : lines);
                double total = cart.items().stream().mapToDouble(l -> l.unitPrice() * l.quantity()).sum();
                sb.append("|total_carrito=").append(String.format(Locale.ROOT, "%.2f", total));
            } catch (Exception ignored) {
                sb.append("|carrito=na");
            }
        }
        return sb.toString();
    }

    private static String role(User user) {
        if (user.getRole() == null || user.getRole().getName() == null) return "";
        return user.getRole().getName();
    }
}
