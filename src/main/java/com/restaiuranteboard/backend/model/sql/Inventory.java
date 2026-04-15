package com.restaiuranteboard.backend.model.sql;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "inventory")
public class Inventory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    
    private String name;
    private Double stockQuantity;
    private String unit; // GR, ML, UNIDADES
    
    private String category; // NUEVO: Categoría del ingrediente
    private Double price;    // NUEVO: Precio del ingrediente
    
    @Column(columnDefinition = "TEXT", name = "image_base64")
    private String imageBase64; // NUEVO: Foto del ingrediente
    
    private boolean isDeleted = false;
}