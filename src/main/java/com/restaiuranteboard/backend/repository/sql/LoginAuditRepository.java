package com.restaiuranteboard.backend.repository.sql;

import com.restaiuranteboard.backend.model.sql.LoginAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface LoginAuditRepository extends JpaRepository<LoginAudit, Integer> {
    List<LoginAudit> findByAttemptedAtBetween(LocalDateTime from, LocalDateTime to);
}

