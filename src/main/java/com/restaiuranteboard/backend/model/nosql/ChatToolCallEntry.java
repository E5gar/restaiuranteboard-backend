package com.restaiuranteboard.backend.model.nosql;

import lombok.Data;

@Data
public class ChatToolCallEntry {
    private String name;
    private String argumentsJson;
    private String resultJson;
}
