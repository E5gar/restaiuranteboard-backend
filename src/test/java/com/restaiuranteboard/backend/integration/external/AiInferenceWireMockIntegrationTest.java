package com.restaiuranteboard.backend.integration.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaiuranteboard.backend.integration.support.WireMockTestSupport;
import com.restaiuranteboard.backend.model.nosql.AiModelConfig;
import com.restaiuranteboard.backend.model.nosql.Producto;
import com.restaiuranteboard.backend.repository.nosql.AiModelConfigRepository;
import com.restaiuranteboard.backend.repository.nosql.ProductoRepository;
import com.restaiuranteboard.backend.repository.nosql.UserInteractionRepository;
import com.restaiuranteboard.backend.repository.sql.RecipeRepository;
import com.restaiuranteboard.backend.service.AiModelService;
import com.restaiuranteboard.backend.service.AiModelSlot3GridFsService;
import com.restaiuranteboard.backend.service.ContextoInteligenciaService;
import com.restaiuranteboard.backend.service.dashboard.InventoryPredictionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {AiModelService.class, ObjectMapper.class})
@Import(AiInferenceWireMockIntegrationTest.Config.class)
@ActiveProfiles("integration")
class AiInferenceWireMockIntegrationTest extends WireMockTestSupport {

    @Autowired
    private AiModelService aiModelService;

    @Autowired
    private AiModelConfigRepository aiModelConfigRepository;

    @BeforeEach
    void reset() {
        wireMock.resetAll();
    }

    @Test
    void recomendarTop3_usesHuggingFaceResponseWhenAvailable() {
        wireMock.stubFor(post(urlPathEqualTo("/predict"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"scores\":[0.95,0.40]}")));

        List<String> ids = aiModelService.recomendarTop3("user-1");

        assertThat(ids).containsExactly("prod-1", "prod-2");
        wireMock.verify(postRequestedFor(urlPathEqualTo("/predict")));
    }

    @Test
    void recomendarTop3_fallsBackWhenHuggingFaceReturnsError() {
        wireMock.stubFor(post(urlPathEqualTo("/predict"))
                .willReturn(aResponse().withStatus(503).withBody("{\"error\":\"unavailable\"}")));

        List<String> ids = aiModelService.recomendarTop3("user-1");

        assertThat(ids).isNotEmpty();
        assertThat(ids.get(0)).isEqualTo("prod-1");
    }

    @TestConfiguration
    static class Config {
        @Bean
        AiModelConfigRepository aiModelConfigRepository() {
            AiModelConfigRepository repo = mock(AiModelConfigRepository.class);
            AiModelConfig config = new AiModelConfig();
            config.setId("GLOBAL_AI_CONFIG");
            config.setIaActiva(true);
            AiModelConfig.ModelSlot slot1 = new AiModelConfig.ModelSlot();
            slot1.setSlotNumber(1);
            slot1.setSlotEnabled(true);
            slot1.setStatus("ACTIVO");
            slot1.setUploadedAt(LocalDateTime.now());
            slot1.setEncodersFileBase64("dGVzdA==");
            config.setSlots(List.of(slot1));
            when(repo.findById("GLOBAL_AI_CONFIG")).thenReturn(Optional.of(config));
            when(repo.save(any(AiModelConfig.class))).thenAnswer(inv -> inv.getArgument(0));
            return repo;
        }

        @Bean
        ProductoRepository productoRepository() {
            ProductoRepository repo = mock(ProductoRepository.class);
            Producto p1 = new Producto();
            p1.setId("prod-1");
            p1.setName("Arroz con pollo");
            p1.setPrice(25.0);
            p1.setCategory("Plato Principal");
            p1.setDeleted(false);
            Producto p2 = new Producto();
            p2.setId("prod-2");
            p2.setName("Chicha");
            p2.setPrice(5.0);
            p2.setCategory("Bebidas");
            p2.setDeleted(false);
            when(repo.findByIsDeletedFalse()).thenReturn(List.of(p1, p2));
            return repo;
        }

        @Bean
        UserInteractionRepository userInteractionRepository() {
            UserInteractionRepository repo = mock(UserInteractionRepository.class);
            when(repo.findTop100ByUserIdOrderByTimestampDesc(any())).thenReturn(List.of());
            return repo;
        }

        @Bean
        RecipeRepository recipeRepository() {
            return mock(RecipeRepository.class);
        }

        @Bean
        ContextoInteligenciaService contextoInteligenciaService() {
            ContextoInteligenciaService svc = mock(ContextoInteligenciaService.class);
            when(svc.contextoActual()).thenReturn(new ContextoInteligenciaService.ContextoInteligencia(22.0, "TARDE", "SOLEADO", "LUNES"));
            return svc;
        }

        @Bean
        AiModelSlot3GridFsService aiModelSlot3GridFsService() {
            return mock(AiModelSlot3GridFsService.class);
        }

        @Bean
        InventoryPredictionService inventoryPredictionService() {
            return mock(InventoryPredictionService.class);
        }
    }
}
