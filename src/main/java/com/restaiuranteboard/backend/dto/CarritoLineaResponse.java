package com.restaiuranteboard.backend.dto;

public record CarritoLineaResponse(
        String productId,
        int quantity,
        String name,
        double unitPrice,
        String thumbSrc
) {}
