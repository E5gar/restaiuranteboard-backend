package com.restaiuranteboard.backend.config;

import com.restaiuranteboard.backend.service.BackupAutomatizacionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BackupAutomatizacionScheduler {

    private final BackupAutomatizacionService backupAutomatizacionService;

    public BackupAutomatizacionScheduler(BackupAutomatizacionService backupAutomatizacionService) {
        this.backupAutomatizacionService = backupAutomatizacionService;
    }

    @Scheduled(cron = "0 * * * * *")
    public void tick() {
        backupAutomatizacionService.runScheduledIfDue();
    }
}
