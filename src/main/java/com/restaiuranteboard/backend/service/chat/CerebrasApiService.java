package com.restaiuranteboard.backend.service.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class CerebrasApiService {

    private static final String API_URL = "https://api.cerebras.ai/v1/chat/completions";
    private static final String MODEL = "gpt-oss-120b";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.cerebras.primerakey:}")
    private String key1;
    @Value("${app.cerebras.segundakey:}")
    private String key2;
    @Value("${app.cerebras.tercerakey:}")
    private String key3;
    @Value("${app.cerebras.cuartakey:}")
    private String key4;
    @Value("${app.cerebras.quintakey:}")
    private String key5;

    public CerebrasApiService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(8000);
        factory.setReadTimeout(90000);
        this.restTemplate = new RestTemplate(factory);
    }

    public record LlmMessage(String role, String content, List<ToolCallReq> toolCalls) {
    }

    public record ToolCallReq(String id, String name, String arguments) {
    }

    public record LlmResult(LlmMessage message, String finishReason) {
    }

    public LlmResult chatCompletion(int keyIndex, List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
        String apiKey = resolveKey(keyIndex);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("API key no configurada");
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", MODEL);
            body.put("max_tokens", 4096);
            body.set("messages", objectMapper.valueToTree(messages));
            if (tools != null && !tools.isEmpty()) {
                body.set("tools", objectMapper.valueToTree(tools));
                body.put("tool_choice", "auto");
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
            ResponseEntity<String> response = restTemplate.exchange(API_URL, HttpMethod.POST, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalStateException("Respuesta inválida del proveedor");
            }
            return parseResponse(response.getBody());
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage() == null ? "Error de inferencia" : e.getMessage(), e);
        }
    }

    private LlmResult parseResponse(String raw) throws Exception {
        JsonNode root = objectMapper.readTree(raw);
        JsonNode choice = root.path("choices").path(0);
        JsonNode msg = choice.path("message");
        String role = msg.path("role").asText("assistant");
        String content = msg.path("content").isNull() ? null : msg.path("content").asText(null);
        String finish = choice.path("finish_reason").asText("stop");
        List<ToolCallReq> toolCalls = new ArrayList<>();
        JsonNode tc = msg.path("tool_calls");
        if (tc.isArray()) {
            for (JsonNode t : tc) {
                String id = t.path("id").asText("");
                String name = t.path("function").path("name").asText("");
                String args = t.path("function").path("arguments").asText("{}");
                if (!name.isBlank()) {
                    toolCalls.add(new ToolCallReq(id, name, args));
                }
            }
        }
        return new LlmResult(new LlmMessage(role, content, toolCalls), finish);
    }

    public Map<String, Object> toolMessage(String toolCallId, String content) {
        return Map.of("role", "tool", "tool_call_id", toolCallId, "content", content == null ? "" : content);
    }

    public Map<String, Object> assistantToolCallsMessage(LlmMessage msg) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("role", "assistant");
        if (msg.content() != null) {
            node.put("content", msg.content());
        } else {
            node.putNull("content");
        }
        ArrayNode arr = objectMapper.createArrayNode();
        for (ToolCallReq tc : msg.toolCalls()) {
            ObjectNode t = objectMapper.createObjectNode();
            t.put("id", tc.id());
            t.put("type", "function");
            ObjectNode fn = objectMapper.createObjectNode();
            fn.put("name", tc.name());
            fn.put("arguments", tc.arguments());
            t.set("function", fn);
            arr.add(t);
        }
        node.set("tool_calls", arr);
        return objectMapper.convertValue(node, Map.class);
    }

    private String resolveKey(int index) {
        return switch (index) {
            case 1 -> key1;
            case 2 -> key2;
            case 3 -> key3;
            case 4 -> key4;
            case 5 -> key5;
            default -> key1;
        };
    }
}
