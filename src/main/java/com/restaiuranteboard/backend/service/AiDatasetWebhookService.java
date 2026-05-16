package com.restaiuranteboard.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class AiDatasetWebhookService {

    private static final Logger log = LoggerFactory.getLogger(AiDatasetWebhookService.class);

    private final BackupAutomatizacionService backupAutomatizacionService;
    private final B2PresignedUrlService b2PresignedUrlService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public AiDatasetWebhookService(
            BackupAutomatizacionService backupAutomatizacionService,
            B2PresignedUrlService b2PresignedUrlService,
            SimpMessagingTemplate messagingTemplate,
            ObjectMapper objectMapper
    ) {
        this.backupAutomatizacionService = backupAutomatizacionService;
        this.b2PresignedUrlService = b2PresignedUrlService;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean verifySignature(String signature) {
        return backupAutomatizacionService.verifyNotifySecret(signature);
    }

    public void handleWorkflowResult(Map<String, Object> body) {
        String status = body != null && body.get("status") != null ? String.valueOf(body.get("status")) : "unknown";
        int slot = parseSlot(body != null ? body.get("slot") : null);
        String fileName = body != null && body.get("file_name") != null
                ? String.valueOf(body.get("file_name"))
                : String.format("dataset_modelo_%02d.zip", slot);

        Map<String, Object> msg = new HashMap<>();
        msg.put("slot", slot);
        msg.put("fileName", fileName);

        if ("success".equalsIgnoreCase(status)) {
            String key = body != null && body.get("backup_key") != null ? String.valueOf(body.get("backup_key")) : null;
            if (key == null || key.isBlank()) {
                publishFailed(slot, fileName, "No se recibió la clave del archivo en B2.");
                return;
            }
            try {
                String downloadUrl = b2PresignedUrlService.presignedGetUrl(key, Duration.ofHours(24));
                msg.put("kind", "dataset_ready");
                msg.put("downloadUrl", downloadUrl);
                publish(msg);
                log.info("[DATASET] listo slot={} key={}", slot, key);
            } catch (Exception e) {
                log.warn("[DATASET] error URL presignada slot={}: {}", slot, e.getMessage());
                publishFailed(slot, fileName, "No se pudo generar el enlace de descarga.");
            }
            return;
        }

        String detail = body != null && body.get("detail") != null ? String.valueOf(body.get("detail")) : null;
        publishFailed(slot, fileName, detail != null && !detail.isBlank()
                ? detail
                : "La generación del dataset falló en GitHub Actions.");
    }

    private void publishFailed(int slot, String fileName, String message) {
        Map<String, Object> msg = new HashMap<>();
        msg.put("kind", "dataset_failed");
        msg.put("slot", slot);
        msg.put("fileName", fileName);
        msg.put("message", message);
        publish(msg);
    }

    private void publish(Map<String, Object> msg) {
        try {
            messagingTemplate.convertAndSend("/topic/admin/dataset", objectMapper.writeValueAsString(msg));
        } catch (Exception e) {
            log.error("[DATASET] No se pudo publicar por WebSocket", e);
        }
    }

    private int parseSlot(Object raw) {
        if (raw == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(raw));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
