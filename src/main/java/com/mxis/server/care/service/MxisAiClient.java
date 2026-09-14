package com.mxis.server.care.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mxis.server.care.config.MxisAiServiceProperties;
import com.mxis.server.care.dto.AiCareRequest;
import com.mxis.server.care.dto.AiCareSummaryResponse;
import com.mxis.server.care.dto.SensorPeriod;
import com.mxis.server.product.entity.Product;
import com.mxis.server.sensor.entity.SensorReading;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MxisAiClient {
    private static final ZoneId AI_TIME_ZONE = ZoneId.of("Asia/Seoul");
    private final MxisAiServiceProperties properties;
    private final ObjectMapper objectMapper;
    private final AiResponseMapper responseMapper;
    private final HttpClient client;

    @Autowired
    public MxisAiClient(MxisAiServiceProperties properties, ObjectMapper objectMapper, AiResponseMapper responseMapper) {
        this(properties, objectMapper, responseMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds())).build());
    }

    MxisAiClient(MxisAiServiceProperties properties, ObjectMapper objectMapper,
                 AiResponseMapper responseMapper, HttpClient client) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.responseMapper = responseMapper;
        this.client = client;
    }

    public boolean isEnabled() { return properties.isEnabled(); }

    public AiCareSummaryResponse getCareSummary(Product product, Long deviceId, SensorPeriod period, List<SensorReading> readings) {
        return getCareSummaryResult(product, deviceId, period, readings).summary();
    }

    public CareSummaryResult getCareSummaryResult(Product product, Long deviceId, SensorPeriod period, List<SensorReading> readings) {
        try {
            String baseUrl = properties.getBaseUrl().replaceAll("/+$", "");
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + "/ai/care-summary"))
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(toRequest(product, deviceId, period, readings))));
            if (properties.hasInternalApiKey()) request.header("X-MXIS-AI-Key", properties.getInternalApiKey().trim());
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AiServiceException(AiServiceException.Category.UNAVAILABLE, "AI service HTTP " + response.statusCode());
            }
            return responseMapper.read(product.getId(), period, response.body());
        } catch (HttpTimeoutException ex) {
            throw new AiServiceException(AiServiceException.Category.TIMEOUT, "AI service timed out", ex);
        } catch (JsonProcessingException ex) {
            throw new AiServiceException(AiServiceException.Category.INVALID_RESPONSE, "AI request serialization failed", ex);
        } catch (IOException ex) {
            throw new AiServiceException(AiServiceException.Category.UNAVAILABLE, "AI service connection failed", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiServiceException(AiServiceException.Category.INTERRUPTED, "AI service call interrupted", ex);
        }
    }

    private AiCareRequest toRequest(Product product, Long deviceId, SensorPeriod period, List<SensorReading> readings) {
        List<AiCareRequest.Reading> items = readings.stream().map(reading -> new AiCareRequest.Reading(
                reading.getSequenceNumber(), reading.getMeasuredAt().atZone(AI_TIME_ZONE).toEpochSecond(),
                reading.getTemperature(), reading.getHumidity(), reading.getMaxShockLevel(),
                reading.getMotionCount() == null ? 0 : reading.getMotionCount())).toList();
        return new AiCareRequest(String.valueOf(product.getId()), product.getProductName(),
                deviceId == null ? "" : String.valueOf(deviceId), product.getMaterialId(),
                product.getMaterialSubtypes() == null ? List.of() : product.getMaterialSubtypes(),
                product.getColor() == null ? "" : product.getColor(), period.days(), 600, items,
                Map.of(), Map.of(), new AiCareRequest.Llm(properties.isLlmEnabled(), properties.getModel(), "ko-KR",
                        Math.max(1, properties.getTimeoutSeconds() - properties.getConnectTimeoutSeconds()),
                        List.of("home_summary", "diagnosis_home", "care_report", "environment_detail", "care_guide", "reservation_cta")));
    }

    public record CareSummaryResult(AiCareSummaryResponse summary, String rawJson, JsonNode root, JsonNode aiCareSummary) { }
}
