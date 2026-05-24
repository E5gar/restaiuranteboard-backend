package com.restaiuranteboard.backend.model.nosql;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "tickets_soporte_counters")
public class TicketSoporteCounter {
    @Id
    private String id;
    private long seq;
}
