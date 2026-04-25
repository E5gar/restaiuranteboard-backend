package com.restaiuranteboard.backend.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaiuranteboard.backend.model.nosql.AiModelConfig;
import com.restaiuranteboard.backend.model.nosql.Producto;
import com.restaiuranteboard.backend.model.nosql.UserInteraction;
import com.restaiuranteboard.backend.model.sql.Inventory;
import com.restaiuranteboard.backend.model.sql.Recipe;
import com.restaiuranteboard.backend.repository.nosql.AiModelConfigRepository;
import com.restaiuranteboard.backend.repository.nosql.ProductoRepository;
import com.restaiuranteboard.backend.repository.nosql.UserInteractionRepository;
import com.restaiuranteboard.backend.repository.sql.RecipeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AiModelService {
    private static final String CONFIG_ID = "GLOBAL_AI_CONFIG";
    private static final String HF_INFERENCE_URL = "https://restaui-backend-inferencia.hf.space/predict";
    private static final double DEFAULT_BASE_SCORE = 0.15d;

    @Autowired
    private AiModelConfigRepository aiModelConfigRepository;
    @Autowired
    private ProductoRepository productoRepository;
    @Autowired
    private UserInteractionRepository userInteractionRepository;
    @Autowired
    private RecipeRepository recipeRepository;
    @Autowired
    private ContextoInteligenciaService contextoInteligenciaService;
    @Autowired
    private ObjectMapper objectMapper;

    private final RestTemplate hfRestTemplate = createHfRestTemplate();

    public Map<String, Object> obtenerConfigAdmin() {
        return toResponse(getOrCreateConfig(), true);
    }

    public Map<String, Object> obtenerConfigPublica() {
        return toResponse(getOrCreateConfig(), false);
    }

    public Map<String, Object> actualizarIaActiva(boolean iaActiva) {
        AiModelConfig config = getOrCreateConfig();
        config.setIaActiva(iaActiva);
        aiModelConfigRepository.save(config);
        return toResponse(config, true);
    }

    public Map<String, Object> subirArchivosSlot1(String modelFileName, String modelFileBase64, String encodersFileName, String encodersFileBase64) {
        if (isBlank(modelFileName) || isBlank(modelFileBase64)) {
            throw new IllegalArgumentException("El archivo del modelo (.keras) es obligatorio.");
        }
        if (isBlank(encodersFileName) || isBlank(encodersFileBase64)) {
            throw new IllegalArgumentException("El archivo de encoders (.json) es obligatorio.");
        }
        if (!modelFileName.toLowerCase().endsWith(".keras")) {
            throw new IllegalArgumentException("El archivo del modelo debe tener extensión .keras.");
        }
        if (!encodersFileName.toLowerCase().endsWith(".json")) {
            throw new IllegalArgumentException("El archivo de encoders debe tener extensión .json.");
        }

        AiModelConfig config = getOrCreateConfig();
        AiModelConfig.ModelSlot slot1 = config.getSlots().stream()
                .filter(s -> s.getSlotNumber() == 1)
                .findFirst()
                .orElseThrow();

        slot1.setStatus("CARGANDO");
        aiModelConfigRepository.save(config);

        slot1.setModelFileName(modelFileName.trim());
        slot1.setModelFileBase64(modelFileBase64.trim());
        slot1.setEncodersFileName(encodersFileName.trim());
        slot1.setEncodersFileBase64(encodersFileBase64.trim());
        slot1.setUploadedAt(LocalDateTime.now());
        slot1.setStatus("ACTIVO");

        aiModelConfigRepository.save(config);
        return toResponse(config, true);
    }

    public List<String> recomendarTop3(String userId) {
        if (isBlank(userId)) return List.of();

        AiModelConfig config = getOrCreateConfig();
        if (!config.isIaActiva()) return List.of();

        AiModelConfig.ModelSlot slot1 = config.getSlots().stream()
                .filter(s -> s.getSlotNumber() == 1)
                .findFirst()
                .orElse(null);
        if (slot1 == null || !"ACTIVO".equalsIgnoreCase(slot1.getStatus())) return List.of();

        List<Producto> productos = productoRepository.findByIsDeletedFalse();
        if (productos.isEmpty()) return List.of();

        List<UserInteraction> interacciones = userInteractionRepository.findTop100ByUserIdOrderByTimestampDesc(userId);
        ContextoInteligenciaService.ContextoInteligencia ctx = contextoInteligenciaService.contextoActual();
        List<String> prediccionesHf = recomendarTop3ConHf(userId, slot1, productos, interacciones, ctx);
        if (!prediccionesHf.isEmpty()) {
            return prediccionesHf;
        }
        return recomendarTop3Heuristico(productos, interacciones, ctx);
    }

    private List<String> recomendarTop3Heuristico(
            List<Producto> productos,
            List<UserInteraction> interacciones,
            ContextoInteligenciaService.ContextoInteligencia ctx
    ) {
        Map<String, Double> puntaje = new HashMap<>();
        for (Producto p : productos) {
            puntaje.put(p.getId(), DEFAULT_BASE_SCORE);
        }

        for (UserInteraction interaccion : interacciones) {
            String productId = interaccion.getProductId();
            if (isBlank(productId) || !puntaje.containsKey(productId)) continue;
            double base = puntaje.get(productId);
            base += pesoAccion(interaccion.getAction());
            Integer dwell = interaccion.getDwellTimeSeconds();
            if (dwell != null && dwell > 0) {
                base += Math.min(0.25d, dwell / 120.0d);
            }
            puntaje.put(productId, base);
        }

        for (Producto p : productos) {
            double score = puntaje.getOrDefault(p.getId(), 0.15d);
            score += bonoContextoCategoria(p.getCategory(), ctx);
            score *= factorRentabilidad(p);
            puntaje.put(p.getId(), score);
        }

        return productos.stream()
                .sorted(Comparator.comparingDouble((Producto p) -> puntaje.getOrDefault(p.getId(), 0d)).reversed())
                .limit(3)
                .map(Producto::getId)
                .collect(Collectors.toList());
    }

    private List<String> recomendarTop3ConHf(
            String userId,
            AiModelConfig.ModelSlot slot1,
            List<Producto> productos,
            List<UserInteraction> interacciones,
            ContextoInteligenciaService.ContextoInteligencia ctx
    ) {
        try {
            EncodersJson encoders = parseEncoders(slot1);
            if (encoders == null || encoders.productIds().isEmpty()) {
                return List.of();
            }
            Map<String, Integer> userMap = toIndexMap(encoders.userIds());
            Map<String, Integer> productMap = toIndexMap(encoders.productIds());
            Map<String, Integer> conditionMap = toIndexMap(encoders.conditions());
            Map<String, Integer> segmentMap = toIndexMap(encoders.segments());
            Map<String, Integer> dayMap = toIndexMap(encoders.days());
            Map<String, Integer> actionMap = toIndexMap(encoders.actions());

            int n = productos.size();
            if (n == 0) return List.of();

            String clima = normalizeToken(ctx.condition());
            String segmento = normalizeToken(ctx.segment());
            String dia = normalizeToken(ctx.day());
            String ultimaAccion = interacciones.isEmpty() ? "VIEW_DETAIL" : normalizeToken(interacciones.get(0).getAction());
            double dwellPromedio = interacciones.stream()
                    .map(UserInteraction::getDwellTimeSeconds)
                    .filter(Objects::nonNull)
                    .mapToDouble(Integer::doubleValue)
                    .average()
                    .orElse(0d);
            float tempNorm = normalizeTemp01(ctx.temp());
            float dwellNorm = normalizeDwell01(dwellPromedio);

            int userEnc = safeEncode(userMap, normalizeToken(userId), 0);
            int conditionEnc = safeEncode(conditionMap, clima, 0);
            int segmentEnc = safeEncode(segmentMap, segmento, 0);
            int dayEnc = safeEncode(dayMap, dia, 0);
            int actionEnc = safeEncode(actionMap, ultimaAccion, 0);

            List<Object> userInput = new ArrayList<>(n);
            List<Object> productInput = new ArrayList<>(n);
            List<Object> conditionInput = new ArrayList<>(n);
            List<Object> segmentInput = new ArrayList<>(n);
            List<Object> dayInput = new ArrayList<>(n);
            List<Object> actionInput = new ArrayList<>(n);
            List<Object> tempInput = new ArrayList<>(n);
            List<Object> dwellInput = new ArrayList<>(n);

            for (int i = 0; i < n; i++) {
                Producto p = productos.get(i);
                userInput.add(userEnc);
                productInput.add(safeEncode(productMap, normalizeToken(p.getId()), 0));
                conditionInput.add(conditionEnc);
                segmentInput.add(segmentEnc);
                dayInput.add(dayEnc);
                actionInput.add(actionEnc);
                tempInput.add(tempNorm);
                dwellInput.add(dwellNorm);
            }

            Map<String, List<Object>> inputs = new LinkedHashMap<>();
            inputs.put("user_input", userInput);
            inputs.put("product_input", productInput);
            inputs.put("condition_input", conditionInput);
            inputs.put("moment_input", segmentInput);
            inputs.put("day_input", dayInput);
            inputs.put("action_input", actionInput);
            inputs.put("temp_input", tempInput);
            inputs.put("dwell_input", dwellInput);

            HfPredictRequest payload = new HfPredictRequest(
                    slot1.getModelFileBase64(),
                    buildModelId(slot1),
                    inputs
            );
            ResponseEntity<HfPredictResponse> response = hfRestTemplate.postForEntity(
                    HF_INFERENCE_URL,
                    payload,
                    HfPredictResponse.class
            );
            HfPredictResponse body = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || body == null || body.scores() == null) {
                return List.of();
            }
            List<Double> scores = body.scores();
            if (scores.size() < n) return List.of();
            Map<String, Double> puntaje = new HashMap<>();
            for (int i = 0; i < n; i++) {
                Producto p = productos.get(i);
                double s = scores.get(i);
                if (Double.isNaN(s) || Double.isInfinite(s)) s = DEFAULT_BASE_SCORE;
                s *= factorRentabilidad(p);
                puntaje.put(p.getId(), s);
            }

            return productos.stream()
                    .sorted(Comparator.comparingDouble((Producto p) -> puntaje.getOrDefault(p.getId(), 0d)).reversed())
                    .limit(3)
                    .map(Producto::getId)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    private EncodersJson parseEncoders(AiModelConfig.ModelSlot slot1) throws IOException {
        byte[] payload = decodeBase64Payload(slot1.getEncodersFileBase64());
        if (payload.length == 0) return null;
        return objectMapper.readValue(payload, EncodersJson.class);
    }

    private byte[] decodeBase64Payload(String value) {
        if (isBlank(value)) return new byte[0];
        String raw = value.trim();
        int comma = raw.indexOf(',');
        if (raw.startsWith("data:") && comma >= 0) {
            raw = raw.substring(comma + 1);
        }
        return Base64.getDecoder().decode(raw);
    }

    private Map<String, Integer> toIndexMap(List<String> classes) {
        Map<String, Integer> map = new HashMap<>();
        if (classes == null) return map;
        for (int i = 0; i < classes.size(); i++) {
            String key = normalizeToken(classes.get(i));
            if (!isBlank(key)) {
                map.put(key, i);
            }
        }
        return map;
    }

    private int safeEncode(Map<String, Integer> map, String value, int fallback) {
        Integer idx = map.get(normalizeToken(value));
        return idx == null ? fallback : idx;
    }

    private String normalizeToken(String v) {
        return v == null ? "" : v.trim().toUpperCase(Locale.ROOT);
    }

    private float normalizeTemp01(Double temp) {
        if (temp == null) return 0.5f;
        double n = (temp - 0d) / 40d;
        return (float) Math.max(0d, Math.min(1d, n));
    }

    private float normalizeDwell01(double dwell) {
        double n = dwell / 300d;
        return (float) Math.max(0d, Math.min(1d, n));
    }

    private String buildModelId(AiModelConfig.ModelSlot slot1) {
        return String.join("|",
                String.valueOf(slot1.getUploadedAt()),
                String.valueOf(slot1.getModelFileName()),
                String.valueOf(slot1.getEncodersFileName())
        );
    }

    private RestTemplate createHfRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2500);
        factory.setReadTimeout(7000);
        return new RestTemplate(factory);
    }

    private double factorRentabilidad(Producto p) {
        if (p.getPrice() == null || p.getPrice() <= 0) return 1.0d;
        List<Recipe> receta = recipeRepository.findByMongoProductIdAndIsDeletedFalse(p.getId());
        double costo = 0d;
        for (Recipe r : receta) {
            Inventory ing = r.getIngredient();
            if (ing == null || ing.getPrice() == null || r.getQuantityToSubtract() == null) continue;
            costo += Math.max(0d, ing.getPrice()) * Math.max(0d, r.getQuantityToSubtract());
        }
        double margen = (p.getPrice() - costo) / p.getPrice();
        double normalizado = Math.max(0d, Math.min(1d, margen));
        return 0.92d + (normalizado * 0.16d);
    }

    private double bonoContextoCategoria(String category, ContextoInteligenciaService.ContextoInteligencia ctx) {
        String cat = category == null ? "" : category.trim().toUpperCase(Locale.ROOT);
        String clima = ctx.condition() == null ? "" : ctx.condition().toUpperCase(Locale.ROOT);
        String segmento = ctx.segment() == null ? "" : ctx.segment().toUpperCase(Locale.ROOT);
        double temp = ctx.temp() == null ? 18d : ctx.temp();
        double b = 0d;

        if ("NOCHE".equals(segmento) && "PLATO PRINCIPAL".equals(cat)) b += 0.14d;
        if ("TARDE".equals(segmento) && "POSTRES".equals(cat)) b += 0.08d;
        if ("MADRUGADA".equals(segmento) && "BEBIDAS".equals(cat)) b += 0.06d;

        if ((clima.contains("LLUVI") || clima.contains("TORMENTA")) && "PLATO PRINCIPAL".equals(cat)) b += 0.12d;
        if (temp >= 23d && "BEBIDAS".equals(cat)) b += 0.14d;
        if (temp <= 14d && "POSTRES".equals(cat)) b += 0.06d;

        return b;
    }

    private double pesoAccion(String action) {
        if (action == null) return 0.02d;
        return switch (action.trim().toUpperCase(Locale.ROOT)) {
            case "PURCHASE_COMPLETED" -> 0.40d;
            case "CHECKOUT_START" -> 0.30d;
            case "ADD_TO_CART", "INCREMENT_QUANTITY" -> 0.24d;
            case "VIEW_DETAIL" -> 0.12d;
            case "IMAGE_SWIPE", "SHARE_PRODUCT" -> 0.07d;
            case "REMOVE_FROM_CART", "REJECT_RECOMMENDATION" -> -0.08d;
            default -> 0.03d;
        };
    }

    private Map<String, Object> toResponse(AiModelConfig config, boolean includeFiles) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("iaActiva", config.isIaActiva());
        List<Map<String, Object>> slots = new ArrayList<>();
        for (AiModelConfig.ModelSlot s : config.getSlots()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("slotNumber", s.getSlotNumber());
            m.put("titulo", s.getTitulo());
            m.put("status", s.getStatus());
            m.put("modelFileName", s.getModelFileName());
            m.put("encodersFileName", s.getEncodersFileName());
            m.put("uploadedAt", s.getUploadedAt());
            if (includeFiles) {
                m.put("modelFileBase64", s.getModelFileBase64());
                m.put("encodersFileBase64", s.getEncodersFileBase64());
            }
            slots.add(m);
        }
        body.put("slots", slots);
        return body;
    }

    private AiModelConfig getOrCreateConfig() {
        return aiModelConfigRepository.findById(CONFIG_ID).orElseGet(() -> {
            AiModelConfig cfg = new AiModelConfig();
            cfg.setId(CONFIG_ID);
            cfg.setIaActiva(false);

            AiModelConfig.ModelSlot slot1 = new AiModelConfig.ModelSlot();
            slot1.setSlotNumber(1);
            slot1.setTitulo("Slot 1: Menú Principal");
            slot1.setStatus("VACIO");

            AiModelConfig.ModelSlot slot2 = new AiModelConfig.ModelSlot();
            slot2.setSlotNumber(2);
            slot2.setTitulo("Slot 2: Próximamente");
            slot2.setStatus("VACIO");

            AiModelConfig.ModelSlot slot3 = new AiModelConfig.ModelSlot();
            slot3.setSlotNumber(3);
            slot3.setTitulo("Slot 3: Próximamente");
            slot3.setStatus("VACIO");

            cfg.setSlots(new ArrayList<>(List.of(slot1, slot2, slot3)));
            return aiModelConfigRepository.save(cfg);
        });
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private record HfPredictRequest(
            @JsonProperty("model_base64") String modelBase64,
            @JsonProperty("model_id") String modelId,
            @JsonProperty("inputs") Map<String, List<Object>> inputs
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record HfPredictResponse(
            @JsonProperty("scores") List<Double> scores
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EncodersJson(
            @JsonProperty("user_id") List<String> userIds,
            @JsonProperty("product_id") List<String> productIds,
            @JsonProperty("condition") List<String> conditions,
            @JsonProperty("segment") List<String> segments,
            @JsonProperty("day") List<String> days,
            @JsonProperty("action") List<String> actions
    ) {
    }
}
