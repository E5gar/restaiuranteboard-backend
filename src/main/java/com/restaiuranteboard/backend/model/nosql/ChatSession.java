package com.restaiuranteboard.backend.model.nosql;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Document(collection = "chat_sessions")
public class ChatSession {
    @Id
    private String id;
    private String userId;
    private String userEmail;
    private String userRole;
    private String chatType;
    private int keyCounter = 1;
    private int userMessageCount;
    private boolean closed;
    private List<ChatMessageEntry> messages = new ArrayList<>();
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
}
