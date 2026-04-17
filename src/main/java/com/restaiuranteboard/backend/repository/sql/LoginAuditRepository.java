package com.restaiuranteboard.backend.repository.sql;

import com.restaiuranteboard.backend.model.sql.LoginAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginAuditRepository extends JpaRepository<LoginAudit, Integer> {
}

