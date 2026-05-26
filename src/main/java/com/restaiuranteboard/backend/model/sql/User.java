package com.restaiuranteboard.backend.model.sql;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "role_id")
    private Role role;

    @Column(unique = true)
    private String email;
    
    private String password;
    
    @Column(unique = true)
    private String dni;
    
    private String fullName;
    private String phone;
    private String address;
    private boolean firstLogin = true;

    @Column(name = "dark_mode")
    private boolean darkMode = false;
    
    private boolean isDeleted = false;
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "email_bounced")
    private boolean emailBounced = false;

    @Column(name = "mfa_enabled")
    private boolean mfaEnabled = false;

    @Column(name = "mfa_secret", length = 64)
    private String mfaSecret;

    @Column(name = "google_sub", unique = true, length = 128)
    private String googleSub;

    @Column(name = "auth_provider", length = 16)
    private String authProvider = "LOCAL";
}