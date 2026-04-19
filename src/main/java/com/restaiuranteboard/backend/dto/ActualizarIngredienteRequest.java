package com.restaiuranteboard.backend.dto;

import lombok.Data;

@Data
public class ActualizarIngredienteRequest {
    private String name;
    private String category;
    private String unit;
    private Double stockQuantity;
    private Double price;
    private String imageBase64;
    /** Obligatorio en true tras mostrar la advertencia de cambio de unidad con recetas activas. */
    private Boolean confirmarCambioUnidad;
}
