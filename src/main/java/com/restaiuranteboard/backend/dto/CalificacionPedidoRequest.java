package com.restaiuranteboard.backend.dto;

public record CalificacionPedidoRequest(
        String userId,
        String orderId,
        int stars,
        String comment
) {}
