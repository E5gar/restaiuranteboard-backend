package com.restaiuranteboard.backend.integration.external;

import com.restaiuranteboard.backend.integration.support.WireMockTestSupport;
import com.restaiuranteboard.backend.service.BackupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import software.amazon.awssdk.services.s3.S3Client;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

@SpringBootTest(classes = {BackupService.class})
@Import(BackupGithubDispatchIntegrationTest.Config.class)
@ActiveProfiles("integration")
class BackupGithubDispatchIntegrationTest extends WireMockTestSupport {

    @Autowired
    private BackupService backupService;

    @DynamicPropertySource
    static void backupJdbc(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/restaiurante_it");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.data.mongodb.uri", () -> "mongodb://localhost:27017/restaiurante_it");
    }

    @BeforeEach
    void reset() {
        wireMock.resetAll();
    }

    @Test
    void generatePairedBackups_dispatchesGithubWorkflowSuccessfully() {
        wireMock.stubFor(post(urlPathEqualTo("/repos/test-owner/test-repo/dispatches"))
                .willReturn(aResponse().withStatus(204)));

        assertThatCode(() -> backupService.generatePairedBackups()).doesNotThrowAnyException();

        wireMock.verify(2, postRequestedFor(urlPathEqualTo("/repos/test-owner/test-repo/dispatches")));
    }

    @TestConfiguration
    static class Config {
        @Bean
        S3Client s3Client() {
            return mock(S3Client.class);
        }
    }
}
