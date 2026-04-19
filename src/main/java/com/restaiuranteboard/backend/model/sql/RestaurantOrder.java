package com.restaiuranteboard.backend.model.sql;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Pedido en PostgreSQL (tabla {@code orders}).
 */
@Data
@Entity
@Table(name = "orders")
public class RestaurantOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false, columnDefinition = "uuid")
    private User client;

    @Column(length = 50, nullable = false)
    private String status = "VALIDANDO_PAGO";

    @Column(name = "total_price", precision = 10, scale = 2, nullable = false)
    private BigDecimal totalPrice;

    @Column(name = "payment_receipt_image", columnDefinition = "bytea")
    private byte[] paymentReceiptImage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
