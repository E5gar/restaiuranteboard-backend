package com.restaiuranteboard.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MaintenanceModeServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private MaintenanceModeService service;

    @BeforeEach
    void setUp() {
        service = new MaintenanceModeService(messagingTemplate);
    }

    @Test
    void startRestore_enablesMaintenanceAndNotifiesClients() {
        service.startRestore(true, "admin@test.com", Set.of("postgres", "mongodb"));

        assertThat(service.isMaintenance()).isTrue();
        assertThat(service.isRestoreComplete()).isFalse();
        verify(messagingTemplate).convertAndSend("/topic/system", "MAINTENANCE_START");
    }

    @Test
    void markRestoreDone_completesWhenAllDatabasesRestored() {
        service.startRestore(false, null, Set.of("postgres"));

        service.markRestoreDone("postgres");

        assertThat(service.isRestoreComplete()).isTrue();
    }

    @Test
    void endMaintenance_disablesMaintenanceAndNotifiesClients() {
        service.startRestore(false, null, Set.of("postgres"));
        service.markRestoreDone("postgres");

        service.endMaintenance();

        assertThat(service.isMaintenance()).isFalse();
        assertThat(service.isRestoreComplete()).isTrue();
        verify(messagingTemplate).convertAndSend("/topic/system", "MAINTENANCE_END");
    }
}
