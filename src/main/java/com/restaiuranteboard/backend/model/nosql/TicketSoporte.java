package com.restaiuranteboard.backend.model.nosql;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "tickets_soporte")
public class TicketSoporte {
    @Id
    private String id;
    private String ticketCode;
    private String clientUserId;
    private String clientEmail;
    private String clientName;
    private String orderId;
    private String orderResumen;
    private String categoria;
    private String descripcion;
    private String evidenciaBase64;
    private String evidenciaMime;
    private String status;
    private String cierreMensaje;
    private String cerradoPorEmail;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
    private Instant closedAt;
}
