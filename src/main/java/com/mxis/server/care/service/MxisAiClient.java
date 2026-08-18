package com.mxis.server.care.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mxis.server.care.config.MxisAiServiceProperties;
import com.mxis.server.care.dto.AiCareSummaryResponse;
import com.mxis.server.care.dto.SensorPeriod;
import com.mxis.server.product.entity.Product;
import com.mxis.server.sensor.entity.SensorReading;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MxisAiClient {

    private static final String INTERNAL_API_KEY_HEADER = "X-MXIS-AI-Key";
    private static final ZoneId AI_TIME_ZONE = ZoneId.of("Asia/Seoul");

    private final MxisAiServiceProperties properties;
    private final ObjectMapper objectMapper;

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public AiCareSummaryResponse getCareSummary(Product product, Long deviceId, SensorPeriod period,
                                                List<SensorReading> readings) {
        return getCareSummaryResult(product, deviceId, period, readings).summary();
    }

    public CareSummaryResult getCareSummaryResult(Product product, Long deviceId, SensorPeriod period,
                                                  List<SensorReading> readings) {
        try {
            JsonNode root = callAiService(product, deviceId, period, readings);
            JsonNode summary = root.path("aiCareSummary");
            if (summary.isMissingNode()) {
                throw new IllegalStateException("AI response does not contain aiCareSummary.");
            }
            return new CareSummaryResult(
                    toResponse(product.getId(), period, summary),
                    objectMapper.writeValueAsString(root),
                    root,
                    summary);
        } catch (IOException ex) {
            throw new IllegalStateException("AI service response parsing failed.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI service call was interrupted.", ex);
        }
    }

    private JsonNode callAiService(Product product, Long deviceId, SensorPeriod period, List<SensorReading> readings)
            throws IOException, InterruptedException {
        String baseUrl = trimTrailingSlash(properties.getBaseUrl());
        URI uri = URI.create(baseUrl + "/ai/care-summary");

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(toRequest(product, deviceId, period, readings))));

        if (properties.hasInternalApiKey()) {
            requestBuilder.header(INTERNAL_API_KEY_HEADER, properties.getInternalApiKey().trim());
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .build();
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("AI service error " + response.statusCode() + ": " + response.body());
        }
        return objectMapper.readTree(response.body());
    }

    private Map<String, Object> toRequest(Product product, Long deviceId, SensorPeriod period,
                                          List<SensorReading> readings) {
        Map<String, Object> llm = new LinkedHashMap<>();
        llm.put("enabled", properties.isLlmEnabled());
        llm.put("model", properties.getModel());
        llm.put("locale", "ko-KR");
        llm.put("timeoutSeconds", properties.getTimeoutSeconds());
        llm.put("screenContexts", List.of(
                "home_summary",
                "diagnosis_home",
                "care_report",
                "environment_detail",
                "care_guide",
                "reservation_cta"));

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("productId", String.valueOf(product.getId()));
        request.put("productName", product.getProductName());
        request.put("deviceId", deviceId == null ? "" : String.valueOf(deviceId));
        request.put("materialId", product.getMaterialId());
        request.put("materialSubtypes", product.getMaterialSubtypes());
        request.put("color", product.getColor() == null ? "" : product.getColor());
        request.put("analysisWindowDays", period.days());
        request.put("samplingWindowSeconds", 600);
        request.put("sensorReadings", readings.stream().map(this::toSensorReading).toList());
        request.put("userEvents", Map.of());
        request.put("userSymptoms", Map.of());
        request.put("llm", llm);
        return request;
    }

    private Map<String, Object> toSensorReading(SensorReading reading) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("sequence", reading.getSequenceNumber());
        item.put("measuredAt", reading.getMeasuredAt().atZone(AI_TIME_ZONE).toEpochSecond());
        item.put("temperature", numberOrNull(reading.getTemperature()));
        item.put("humidity", numberOrNull(reading.getHumidity()));
        item.put("maxShock", numberOrNull(reading.getMaxShockLevel()));
        item.put("motionCount", reading.getMotionCount() == null ? 0 : reading.getMotionCount());
        return item;
    }

    private AiCareSummaryResponse toResponse(Long productId, SensorPeriod period, JsonNode summary) {
        return new AiCareSummaryResponse(
                productId,
                parseDateTime(summary.path("generatedAt").asText(null)),
                summary.path("analysisWindowDays").asInt(period.days()),
                dataSufficiency(summary.path("dataSufficiency")),
                productCondition(summary.path("productCondition")),
                stressLabels(summary.path("stressLabels")),
                explanation(summary.path("explanation")),
                copyGeneration(summary.path("copyGeneration")));
    }

    private AiCareSummaryResponse.DataSufficiency dataSufficiency(JsonNode node) {
        return new AiCareSummaryResponse.DataSufficiency(
                text(node, "status"),
                nullableText(node, "reason"),
                node.path("validReadingCount").asLong(0),
                node.path("coverageHours").isMissingNode() || node.path("coverageHours").isNull()
                        ? null
                        : node.path("coverageHours").asDouble(),
                parseDateTime(node.path("lastMeasuredAt").asText(null)),
                parseDateTime(node.path("lastSyncedAt").asText(null)));
    }

    private AiCareSummaryResponse.ProductCondition productCondition(JsonNode node) {
        return new AiCareSummaryResponse.ProductCondition(
                text(node, "label"),
                nullableInt(node, "score"),
                nullableText(node, "primaryFactor"),
                text(node, "summary"));
    }

    private AiCareSummaryResponse.StressLabels stressLabels(JsonNode node) {
        return new AiCareSummaryResponse.StressLabels(
                text(node, "humidity"),
                text(node, "temperatureHeat"),
                text(node, "dryness"),
                text(node, "handling"),
                text(node, "usageRest"),
                text(node, "uvLight"));
    }

    private AiCareSummaryResponse.Explanation explanation(JsonNode node) {
        return new AiCareSummaryResponse.Explanation(
                text(node, "short"),
                stringList(node.path("reasonBullets")),
                stringList(node.path("sensorLimitations")));
    }

    private AiCareSummaryResponse.CopyGeneration copyGeneration(JsonNode node) {
        return new AiCareSummaryResponse.CopyGeneration(
                text(node, "source"),
                nullableText(node, "model"),
                nullableText(node, "error"));
    }

    private List<String> stringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual()) {
                result.add(item.asText());
            }
        }
        return result;
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank() || "null".equals(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            return java.time.OffsetDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME).toLocalDateTime();
        }
    }

    private String text(JsonNode node, String fieldName) {
        return node.path(fieldName).asText();
    }

    private String nullableText(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private Integer nullableInt(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? null : value.asInt();
    }

    private Object numberOrNull(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://127.0.0.1:8765";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public record CareSummaryResult(
            AiCareSummaryResponse summary,
            String rawJson,
            JsonNode root,
            JsonNode aiCareSummary
    ) {
    }
}
