package com.restaiuranteboard.backend.repository.nosql;

import com.restaiuranteboard.backend.model.nosql.TicketSoporte;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface TicketSoporteRepository extends MongoRepository<TicketSoporte, String> {
    List<TicketSoporte> findByStatusOrderByCreatedAtDesc(String status);

    List<TicketSoporte> findAllByOrderByCreatedAtDesc();

    long countByStatus(String status);
}
