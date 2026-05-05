package com.restaiuranteboard.backend.controller;

import com.restaiuranteboard.backend.dto.BackupItemDto;
import com.restaiuranteboard.backend.service.BackupService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/backups")
public class AdminBackupController {

    private final BackupService backupService;

    public AdminBackupController(BackupService backupService) {
        this.backupService = backupService;
    }

    @GetMapping("/list")
    public ResponseEntity<List<BackupItemDto>> list(@RequestParam String db) {
        return ResponseEntity.ok(backupService.list(db));
    }

    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generate(@RequestParam String db) {
        BackupItemDto item = backupService.generate(db);
        return ResponseEntity.ok(Map.of("ok", true, "item", item));
    }

    @PostMapping("/restore")
    public ResponseEntity<Map<String, Object>> restore(@RequestParam String db, @RequestParam String key) {
        backupService.restore(db, key);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Map<String, Object>> delete(@RequestParam String key) {
        backupService.delete(key);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}

