package com.restaiuranteboard.backend.controller;

import com.restaiuranteboard.backend.service.AiModelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/ia-modelos")
public class AiModelController {

    @Autowired
    private AiModelService aiModelService;

    @GetMapping
    public ResponseEntity<?> obtenerConfigAdmin() {
        return ResponseEntity.ok(aiModelService.obtenerConfigAdmin());
    }

    @GetMapping("/publico")
    public ResponseEntity<?> obtenerConfigPublica() {
        return ResponseEntity.ok(aiModelService.obtenerConfigPublica());
    }

    @PatchMapping("/toggle")
    public ResponseEntity<?> toggleIa(@RequestBody Map<String, Object> body) {
        Object val = body == null ? null : body.get("iaActiva");
        if (val == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "iaActiva es obligatorio."));
        }
        boolean iaActiva = val instanceof Boolean ? (Boolean) val : Boolean.parseBoolean(String.valueOf(val));
        return ResponseEntity.ok(aiModelService.actualizarIaActiva(iaActiva));
    }

    @PatchMapping("/slot/{slotNumber}/toggle")
    public ResponseEntity<?> toggleSlot(@PathVariable int slotNumber, @RequestBody Map<String, Object> body) {
        Object val = body == null ? null : body.get("enabled");
        if (val == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "enabled es obligatorio."));
        }
        boolean enabled = val instanceof Boolean ? (Boolean) val : Boolean.parseBoolean(String.valueOf(val));
        return ResponseEntity.ok(aiModelService.actualizarSlotEnabled(slotNumber, enabled));
    }

    @PostMapping("/slot-1/upload")
    public ResponseEntity<?> subirSlot1(@RequestBody Map<String, String> body) {
        try {
            String modelFileName = body == null ? null : body.get("modelFileName");
            String modelFileBase64 = body == null ? null : body.get("modelFileBase64");
            String encodersFileName = body == null ? null : body.get("encodersFileName");
            String encodersFileBase64 = body == null ? null : body.get("encodersFileBase64");
            return ResponseEntity.ok(aiModelService.subirArchivosSlot1(
                    modelFileName,
                    modelFileBase64,
                    encodersFileName,
                    encodersFileBase64
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "No se pudo cargar el modelo."));
        }
    }

    @PostMapping("/slot-2/upload")
    public ResponseEntity<?> subirSlot2(@RequestBody Map<String, String> body) {
        try {
            return ResponseEntity.ok(aiModelService.subirArchivosSlot2(
                    body == null ? null : body.get("rulesFileName"),
                    body == null ? null : body.get("rulesFileBase64"),
                    body == null ? null : body.get("frequencyFileName"),
                    body == null ? null : body.get("frequencyFileBase64"),
                    body == null ? null : body.get("configFileName"),
                    body == null ? null : body.get("configFileBase64")
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "No se pudo cargar el paquete de reglas del Slot 2."));
        }
    }
}
