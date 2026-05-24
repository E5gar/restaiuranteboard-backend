package com.restaiuranteboard.backend.repository.nosql;

import com.restaiuranteboard.backend.model.nosql.TicketSoporteCounter;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TicketSoporteCounterRepository extends MongoRepository<TicketSoporteCounter, String> {
}
