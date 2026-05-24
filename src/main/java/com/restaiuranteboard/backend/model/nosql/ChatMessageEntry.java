package com.restaiuranteboard.backend.model.nosql;

import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
public class ChatMessageEntry {
    private String sender;
    private String content;
    private List<ChatToolCallEntry> toolCalls = new ArrayList<>();
    private Instant timestamp = Instant.now();
}
