package com.restaiuranteboard.backend.integration.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaiuranteboard.backend.integration.support.WireMockTestSupport;
import com.restaiuranteboard.backend.model.nosql.EmailDispatchLog;
import com.restaiuranteboard.backend.repository.nosql.EmailDispatchLogRepository;
import com.restaiuranteboard.backend.service.GithubEmailDispatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {GithubEmailDispatchService.class, ObjectMapper.class})
@Import(GithubEmailDispatchIntegrationTest.Config.class)
@ActiveProfiles("integration")
class GithubEmailDispatchIntegrationTest extends WireMockTestSupport {

    private static final Map<String, EmailDispatchLog> LOG_STORE = new ConcurrentHashMap<>();

    @Autowired
    private GithubEmailDispatchService githubEmailDispatchService;

    @BeforeEach
    void reset() {
        LOG_STORE.clear();
        wireMock.resetAll();
    }

    @Test
    void dispatchPlainEmail_postsToGithubActionsSuccessfully() {
        wireMock.stubFor(post(urlPathEqualTo("/repos/test-owner/test-repo/dispatches"))
                .willReturn(aResponse().withStatus(204)));

        githubEmailDispatchService.dispatchPlainEmail(
                "cliente@gmail.com",
                "negocio@gmail.com",
                "app-password-16chars",
                "Confirmación",
                "Su ticket fue registrado",
                null
        );

        assertThat(LOG_STORE).hasSize(1);
        EmailDispatchLog log = LOG_STORE.values().iterator().next();
        assertThat(log.getStatus()).isEqualTo("DISPATCHED");
        wireMock.verify(postRequestedFor(urlPathEqualTo("/repos/test-owner/test-repo/dispatches"))
                .withHeader("Authorization", equalTo("Bearer test-github-token")));
    }

    @Test
    void dispatchPlainEmail_recordsErrorWhenGithubReturns500() {
        wireMock.stubFor(post(urlPathEqualTo("/repos/test-owner/test-repo/dispatches"))
                .willReturn(aResponse().withStatus(500).withBody("{\"message\":\"error\"}")));

        assertThatThrownBy(() -> githubEmailDispatchService.dispatchPlainEmail(
                "cliente@gmail.com",
                "negocio@gmail.com",
                "app-password-16chars",
                "Asunto",
                "Cuerpo",
                null
        )).hasMessageContaining("Error al encolar correo");

        assertThat(LOG_STORE).hasSize(1);
        assertThat(LOG_STORE.values().iterator().next().getStatus()).isEqualTo("DISPATCH_ERROR");
    }

    @TestConfiguration
    static class Config {
        @Bean
        EmailDispatchLogRepository emailDispatchLogRepository() {
            EmailDispatchLogRepository repo = mock(EmailDispatchLogRepository.class);
            when(repo.save(any(EmailDispatchLog.class))).thenAnswer(invocation -> {
                EmailDispatchLog entity = invocation.getArgument(0);
                LOG_STORE.put(entity.getTrackingId(), entity);
                return entity;
            });
            when(repo.findById(anyString())).thenAnswer(invocation ->
                    Optional.ofNullable(LOG_STORE.get(invocation.getArgument(0))));
            return repo;
        }
    }
}
