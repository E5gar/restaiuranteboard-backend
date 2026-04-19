package com.restaiuranteboard.backend.dto;

public record CajaLineaDetalle(
        String nombreProducto,
        int cantidad,
        String precioUnitario,
        String subtotal
) {}
