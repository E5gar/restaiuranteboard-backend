package com.restaiuranteboard.backend.model.nosql;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "email_dispatch_logs")
public class EmailDispatchLog {
    @Id
    private String trackingId;
    private String toEmail;
    private String subject;
    private String notifyUserId;
    private String status;
    private Integer smtpCode;
    private String errorDetail;
    private Instant createdAt;
    private Instant updatedAt;
}
