package com.restaiuranteboard.backend.model.nosql;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Document(collection = "ai_model_config")
public class AiModelConfig {
    @Id
    private String id;
    private boolean iaActiva = false;
    private List<ModelSlot> slots = new ArrayList<>();

    @Data
    public static class ModelSlot {
        private int slotNumber;
        private String titulo;
        private String status;
        private String modelFileName;
        private String modelFileBase64;
        private String encodersFileName;
        private String encodersFileBase64;
        private LocalDateTime uploadedAt;
    }
}
