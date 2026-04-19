package com.restaiuranteboard.backend.model.sql;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Pedido en PostgreSQL (tabla {@code orders}).
 */
@Data
@Entity
@Table(name = "orders")
public class RestaurantOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 50)
    private String status = "PENDIENTE_PAGO";
}
