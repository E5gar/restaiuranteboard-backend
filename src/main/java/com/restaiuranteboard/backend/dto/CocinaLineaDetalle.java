package com.restaiuranteboard.backend.dto;

import java.util.List;

public record CocinaLineaDetalle(
        String productoMongoId,
        String productoNombre,
        int cantidad,
        List<CocinaIngredienteDetalle> ingredientes
) {}
