package com.restaiuranteboard.backend.dto;

import java.time.LocalDateTime;

public record BackupItemDto(
        String key,
        long sizeBytes,
        LocalDateTime lastModified
) {
}

