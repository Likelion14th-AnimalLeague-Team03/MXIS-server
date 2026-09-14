package com.mxis.server.care.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mxis.server.care.dto.AiCareSummaryResponse;
import com.mxis.server.care.dto.SensorPeriod;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The only place where untrusted AI JSON becomes a backend care summary. */
@Component
@RequiredArgsConstructor
public class AiResponseMapper {
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final Set<String> DATA_STATUS = Set.of("NO_DATA", "INSUFFICIENT_DATA", "STALE_DATA", "SUFFICIENT");
    private static final Set<String> LABELS = Set.of("Excellent", "Standard", "Needs Attention", "Collecting Data");
    private static final Set<String> STRESS = Set.of("LOW", "CAUTION", "ELEVATED", "HIGH", "INSPECTION_REQUIRED", "UNKNOWN");
    private final ObjectMapper objectMapper;

    public MxisAiClient.CareSummaryResult read(Long productId, SensorPeriod period, String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isObject()) throw invalid("response must be an object");
            JsonNode summary = object(root, "aiCareSummary");
            validateIdentity(root, productId);
            validateIdentity(summary, productId);
            JsonNode data = object(summary, "dataSufficiency");
            String status = enumValue(data, "status", DATA_STATUS);
            long count = nonNegativeLong(data, "validReadingCount");
            Double coverage = nullableNumber(data, "coverageHours");
            if (coverage != null && (!Double.isFinite(coverage) || coverage < 0)) throw invalid("coverageHours is invalid");
            JsonNode condition = object(summary, "productCondition");
            String label = enumValue(condition, "label", LABELS);
            Integer score = nullableScore(condition);
            if ("SUFFICIENT".equals(status)) {
                if (count < CareDecisionPolicy.MIN_VALID_READINGS || coverage == null
                        || coverage < CareDecisionPolicy.MIN_COVERAGE_HOURS
                        || score == null || "Collecting Data".equals(label)) throw invalid("inconsistent sufficient result");
            } else if (score != null || !"Collecting Data".equals(label)) {
                throw invalid("non-sufficient result must have a null score and Collecting Data label");
            }
            if (summary.has("careDecision") && !summary.get("careDecision").isNull()) {
                JsonNode decision = object(summary, "careDecision");
                String careNeed = enumValue(decision, "careNeed", Set.of("NONE", "LOW", "LOW_MEDIUM", "MEDIUM", "MEDIUM_HIGH", "HIGH", "UNKNOWN"));
                String inspection = enumValue(decision, "inspectionNeed", Set.of("NONE", "CONDITIONAL", "REQUIRED", "UNKNOWN"));
                if ("SUFFICIENT".equals(status) && ("UNKNOWN".equals(careNeed) || "UNKNOWN".equals(inspection))) {
                    throw invalid("sufficient result cannot contain an unknown care decision");
                }
            }
            int days = Math.toIntExact(nonNegativeLong(summary, "analysisWindowDays"));
            if (days != period.days()) throw invalid("analysis window does not match request");
            JsonNode stress = object(summary, "stressLabels");
            JsonNode explanation = object(summary, "explanation");
            JsonNode copy = summary.path("copyGeneration");
            AiCareSummaryResponse response = new AiCareSummaryResponse(
                    productId, requiredDate(summary, "generatedAt"), days,
                    new AiCareSummaryResponse.DataSufficiency(status, optionalText(data, "reason"), count, coverage,
                            optionalDate(data, "lastMeasuredAt"), optionalDate(data, "lastSyncedAt")),
                    new AiCareSummaryResponse.ProductCondition(label, score, optionalText(condition, "primaryFactor"), requiredText(condition, "summary")),
                    new AiCareSummaryResponse.StressLabels(enumValue(stress, "humidity", STRESS),
                            enumValue(stress, "temperatureHeat", STRESS), enumValue(stress, "dryness", STRESS),
                            enumValue(stress, "handling", STRESS), enumValue(stress, "usageRest", STRESS), enumValue(stress, "uvLight", STRESS)),
                    new AiCareSummaryResponse.Explanation(requiredText(explanation, "short"), strings(explanation, "reasonBullets"), strings(explanation, "sensorLimitations")),
                    new AiCareSummaryResponse.CopyGeneration(copy.isObject() ? requiredText(copy, "source") : "ai_service",
                            optionalText(copy, "model"), optionalText(copy, "error")));
            return new MxisAiClient.CareSummaryResult(response, json, root, summary);
        } catch (IOException | ArithmeticException | DateTimeParseException ex) {
            throw new AiServiceException(AiServiceException.Category.INVALID_RESPONSE, "Invalid AI response format", ex);
        }
    }

    private JsonNode object(JsonNode parent, String field) {
        JsonNode value = parent.path(field);
        if (!value.isObject()) throw invalid("missing object: " + field);
        return value;
    }

    private void validateIdentity(JsonNode node, Long productId) {
        JsonNode supplied = node.path("productId");
        if (!supplied.isMissingNode() && !supplied.isNull()
                && !String.valueOf(productId).equals(supplied.asText())) {
            throw invalid("product identity does not match request");
        }
    }

    private String requiredText(JsonNode node, String field) {
        String value = optionalText(node, field);
        if (value == null || value.isBlank()) throw invalid("missing text: " + field);
        return value;
    }

    private String optionalText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        if (!value.isTextual()) throw invalid("invalid text: " + field);
        return value.asText();
    }

    private String enumValue(JsonNode node, String field, Set<String> allowed) {
        String value = requiredText(node, field);
        if (!allowed.contains(value)) throw invalid("unsupported value: " + field);
        return value;
    }

    private long nonNegativeLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.asLong() < 0) throw invalid("invalid integer: " + field);
        return value.asLong();
    }

    private Double nullableNumber(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        if (!value.isNumber()) throw invalid("invalid number: " + field);
        return value.asDouble();
    }

    private Integer nullableScore(JsonNode node) {
        JsonNode value = node.path("score");
        if (value.isMissingNode() || value.isNull()) return null;
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() < 0 || value.asInt() > 100) throw invalid("invalid score");
        return value.asInt();
    }

    private List<String> strings(JsonNode node, String field) {
        JsonNode values = node.path(field);
        if (!values.isArray()) throw invalid("missing array: " + field);
        List<String> result = new ArrayList<>();
        for (JsonNode value : values) {
            if (!value.isTextual()) throw invalid("invalid array value: " + field);
            result.add(value.asText());
        }
        return List.copyOf(result);
    }

    private LocalDateTime requiredDate(JsonNode node, String field) {
        return parseDate(requiredText(node, field));
    }

    private LocalDateTime optionalDate(JsonNode node, String field) {
        String value = optionalText(node, field);
        return value == null ? null : parseDate(value);
    }

    private LocalDateTime parseDate(String value) {
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(ZONE).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            return LocalDateTime.parse(value);
        }
    }

    private AiServiceException invalid(String message) {
        return new AiServiceException(AiServiceException.Category.INVALID_RESPONSE, message);
    }
}
