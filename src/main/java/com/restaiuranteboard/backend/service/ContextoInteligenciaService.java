package com.restaiuranteboard.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.LocalDateTime;

@Service
public class ContextoInteligenciaService {

    public record ContextoInteligencia(
            Double temp,
            String condition,
            String day,
            String segment
    ) {}

    private static final String OPEN_METEO_URL =
            "https://api.open-meteo.com/v1/forecast?latitude=-12.0686&longitude=-75.2102&current_weather=true";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ContextoInteligencia contextoActual() {
        LocalDateTime now = LocalDateTime.now();
        Double temp = null;
        String condition = "DESCONOCIDO";
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(OPEN_METEO_URL)).GET().build();
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                JsonNode root = objectMapper.readTree(res.body());
                JsonNode cw = root.path("current_weather");
                if (!cw.isMissingNode()) {
                    if (cw.has("temperature") && cw.get("temperature").isNumber()) {
                        temp = cw.get("temperature").asDouble();
                    }
                    int weatherCode = cw.path("weathercode").asInt(-1);
                    condition = mapWeatherCode(weatherCode);
                }
            }
        } catch (Exception ignored) {
        }
        return new ContextoInteligencia(
                temp,
                condition,
                mapDay(now.getDayOfWeek()),
                mapSegment(now.getHour())
        );
    }

    private String mapSegment(int hora) {
        if (hora >= 0 && hora < 6) return "MADRUGADA";
        if (hora >= 6 && hora < 12) return "MAÑANA";
        if (hora >= 12 && hora < 18) return "TARDE";
        return "NOCHE";
    }

    private String mapDay(DayOfWeek d) {
        return switch (d) {
            case MONDAY -> "LUNES";
            case TUESDAY -> "MARTES";
            case WEDNESDAY -> "MIERCOLES";
            case THURSDAY -> "JUEVES";
            case FRIDAY -> "VIERNES";
            case SATURDAY -> "SABADO";
            case SUNDAY -> "DOMINGO";
        };
    }

    private String mapWeatherCode(int code) {
        if (code < 0) return "DESCONOCIDO";
        if (code == 0) return "SOLEADO";
        if (code == 1 || code == 2) return "PARCIALMENTE_NUBLADO";
        if (code == 3 || code == 45 || code == 48) return "NUBLADO";
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return "LLUVIOSO";
        if (code >= 95) return "TORMENTA";
        return "OTRO";
    }
}
