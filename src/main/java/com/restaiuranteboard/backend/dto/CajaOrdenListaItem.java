package com.restaiuranteboard.backend.dto;

public record CajaOrdenListaItem(
        String id,
        String createdAt,
        String clienteNombre,
        String total
) {}
