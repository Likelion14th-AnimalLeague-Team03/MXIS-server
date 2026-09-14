package com.mxis.server.care.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Wire names intentionally match the existing Python /ai/care-summary contract. */
public record AiCareRequest(
        String productId, String productName, String deviceId, String materialId,
        List<String> materialSubtypes, String color, int analysisWindowDays,
        int samplingWindowSeconds, List<Reading> sensorReadings,
        Map<String, Object> userEvents, Map<String, Object> userSymptoms, Llm llm) {
    public record Reading(Long sequence, long measuredAt, BigDecimal temperature,
                          BigDecimal humidity, BigDecimal maxShock, int motionCount) { }
    public record Llm(boolean enabled, String model, String locale,
                      int timeoutSeconds, List<String> screenContexts) { }
}
