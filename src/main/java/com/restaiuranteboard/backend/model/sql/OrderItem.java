package com.restaiuranteboard.backend.model.sql;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Línea de pedido vinculada al producto MongoDB ({@code mongo_product_id}).
 */
@Data
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, columnDefinition = "uuid")
    private RestaurantOrder restaurantOrder;

    @Column(name = "mongo_product_id", nullable = false, length = 64)
    private String mongoProductId;

    @Column(nullable = false)
    private Integer quantity = 1;

    /** Precio unitario capturado al crear la línea. */
    @Column(name = "price_at_moment", precision = 12, scale = 2, nullable = false)
    private BigDecimal priceAtMoment;
}
