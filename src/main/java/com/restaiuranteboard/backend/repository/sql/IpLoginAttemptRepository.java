package com.restaiuranteboard.backend.repository.sql;

import com.restaiuranteboard.backend.model.sql.IpLoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IpLoginAttemptRepository extends JpaRepository<IpLoginAttempt, Integer> {
    Optional<IpLoginAttempt> findByIpAddress(String ipAddress);
}

