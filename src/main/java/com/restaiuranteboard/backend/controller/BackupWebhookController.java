package com.restaiuranteboard.backend.controller;

import com.restaiuranteboard.backend.service.BackupAutomatizacionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
public class BackupWebhookController {

    private final BackupAutomatizacionService backupAutomatizacionService;

    public BackupWebhookController(BackupAutomatizacionService backupAutomatizacionService) {
        this.backupAutomatizacionService = backupAutomatizacionService;
    }

    @PostMapping("/backup-workflow")
    public ResponseEntity<Map<String, Object>> workflow(
            @RequestHeader(value = "X-Backup-Signature", required = false) String signature,
            @RequestBody(required = false) Map<String, Object> body) {
        if (!backupAutomatizacionService.verifyNotifySecret(signature)) {
            return ResponseEntity.status(401).body(Map.of("ok", false));
        }
        String status = body != null && body.get("status") != null ? String.valueOf(body.get("status")) : "unknown";
        String detail = body != null && body.get("detail") != null ? String.valueOf(body.get("detail")) : null;
        backupAutomatizacionService.recordWorkflowResult(status, detail);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/backup-cron")
    public ResponseEntity<Map<String, Object>> cron(
            @RequestHeader(value = "X-Cron-Secret", required = false) String secret) {
        try {
            backupAutomatizacionService.runFromExternalCron(secret);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401).body(Map.of("ok", false));
        }
    }
}
