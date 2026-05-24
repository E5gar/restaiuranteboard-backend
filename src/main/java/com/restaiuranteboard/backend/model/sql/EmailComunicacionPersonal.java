package com.restaiuranteboard.backend.model.sql;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "email_comunicacion_personal")
public class EmailComunicacionPersonal {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "admin_email", nullable = false)
    private String adminEmail;

    @Column(name = "recipient_email", nullable = false)
    private String recipientEmail;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String contenido;

    private LocalDateTime createdAt = LocalDateTime.now();
}
