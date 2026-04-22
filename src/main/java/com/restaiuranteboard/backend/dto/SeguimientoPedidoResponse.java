package com.restaiuranteboard.backend.dto;

import java.util.List;

public record SeguimientoPedidoResponse(
        String orderId,
        String estado,
        String createdAt,
        String total,
        String cancelReason,
        String repartidorNombre,
        List<CajaLineaDetalle> lineas
) {}
