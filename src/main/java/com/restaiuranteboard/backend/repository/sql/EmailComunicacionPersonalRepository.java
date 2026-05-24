package com.restaiuranteboard.backend.repository.sql;

import com.restaiuranteboard.backend.model.sql.EmailComunicacionPersonal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EmailComunicacionPersonalRepository extends JpaRepository<EmailComunicacionPersonal, UUID> {
}
