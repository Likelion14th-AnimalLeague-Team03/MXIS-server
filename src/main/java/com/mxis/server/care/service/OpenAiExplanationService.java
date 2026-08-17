package com.mxis.server.care.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mxis.server.care.config.OpenAiProperties;
import com.mxis.server.care.dto.AiCareSummaryResponse;
import com.mxis.server.care.dto.OpenAiStatusResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OpenAiExplanationService {

    private static final URI OPENAI_RESPONSES_URI = URI.create("https://api.openai.com/v1/responses");
    private static final List<String> FORBIDDEN_CLAIMS = List.of(
            "손상되었습니다",
            "곰팡이가 생겼습니다",
            "갈라졌습니다",
            "확률",
            "수리비",
            "보증",
            "정품",
            "가품",
            "2g 이상이면 손상");

    private static final String SYSTEM_PROMPT = """
            You are MXIS Care Explanation Writer.

            Your job is to convert structured material-care analysis into concise Korean user-facing copy.
            You must not create new risk judgments, probabilities, thresholds, diagnoses, repair estimates, or inspection requirements.
            Use only the provided structured input.

            The product is a luxury bag. Write calmly, precisely, and preventively.
            Do not overstate damage. Sensor-only exposure means exposure, not confirmed damage.

            Return valid JSON only.
            """;

    private static final String DEVELOPER_PROMPT = """
            Follow these rules:

            1. Output language must be Korean.
            2. Use polite but concise Korean.
            3. Do not mention AI.
            4. Never output damage probability, mould probability, cracking probability, repair cost, warranty advice, or brand-authenticity advice.
            5. If dataSufficiency.status is not SUFFICIENT, focus on data collection status and avoid care conclusions.
            6. If uvLight is UNKNOWN because light is not measured, say that UV/light is not directly measured by the current sensor.
            7. If handling is based on IMU, explain it as movement/usage exposure rather than shock damage.
            8. Keep the output within the schema. Do not add extra fields.
            """;

    private final OpenAiProperties properties;
    private final ObjectMapper objectMapper;

    public OpenAiStatusResponse getStatus() {
        return new OpenAiStatusResponse(
                properties.isEnabled(),
                properties.hasApiKey(),
                properties.getModel(),
                properties.getTimeoutSeconds());
    }

    public AiCareSummaryResponse applyOpenAiCopy(AiCareSummaryResponse fallback) {
        if (!properties.isEnabled() || !properties.hasApiKey()) {
            return fallback;
        }

        try {
            GeneratedExplanation generated = generate(fallback);
            return new AiCareSummaryResponse(
                    fallback.productId(),
                    fallback.generatedAt(),
                    fallback.analysisWindowDays(),
                    fallback.dataSufficiency(),
                    fallback.productCondition(),
                    fallback.stressLabels(),
                    new AiCareSummaryResponse.Explanation(
                            generated.shortText(),
                            generated.reasonBullets(),
                            generated.sensorLimitations()),
                    new AiCareSummaryResponse.CopyGeneration("openai", properties.getModel(), null));
        } catch (Exception ex) {
            return new AiCareSummaryResponse(
                    fallback.productId(),
                    fallback.generatedAt(),
                    fallback.analysisWindowDays(),
                    fallback.dataSufficiency(),
                    fallback.productCondition(),
                    fallback.stressLabels(),
                    fallback.explanation(),
                    new AiCareSummaryResponse.CopyGeneration(
                            "deterministic_fallback",
                            properties.getModel(),
                            sanitizeError(ex.getMessage())));
        }
    }

    private GeneratedExplanation generate(AiCareSummaryResponse fallback) throws IOException, InterruptedException {
        Map<String, Object> requestBody = Map.of(
                "model", properties.getModel(),
                "input", List.of(
                        message("system", SYSTEM_PROMPT),
                        message("developer", DEVELOPER_PROMPT),
                        message("user", "Create MXIS explanation JSON for this structured analysis.\n\n"
                                + objectMapper.writeValueAsString(openAiInput(fallback)))),
                "text", Map.of(
                        "format", Map.of(
                                "type", "json_schema",
                                "name", "mxis_care_summary_explanation",
                                "strict", true,
                                "schema", explanationSchema())));

        HttpRequest request = HttpRequest.newBuilder(OPENAI_RESPONSES_URI)
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .header("Authorization", "Bearer " + properties.getApiKey().trim())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                .build();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("OpenAI API error " + response.statusCode() + ": "
                    + sanitizeError(response.body()));
        }

        String outputText = extractOutputText(objectMapper.readTree(response.body()));
        JsonNode generated = objectMapper.readTree(outputText);
        validateGenerated(generated, fallback);

        return new GeneratedExplanation(
                requiredText(generated, "short"),
                requiredTextArray(generated, "reasonBullets"),
                requiredTextArray(generated, "sensorLimitations"));
    }

    private Map<String, Object> openAiInput(AiCareSummaryResponse fallback) {
        return Map.of(
                "locale", "ko-KR",
                "analysis", Map.of(
                        "dataSufficiency", fallback.dataSufficiency(),
                        "productCondition", fallback.productCondition(),
                        "stressLabels", fallback.stressLabels(),
                        "existingFallbackExplanation", fallback.explanation()));
    }

    private Map<String, Object> message(String role, String text) {
        return Map.of(
                "role", role,
                "content", List.of(Map.of("type", "input_text", "text", text)));
    }

    private Map<String, Object> explanationSchema() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("short", "reasonBullets", "sensorLimitations"),
                "properties", Map.of(
                        "short", Map.of("type", "string"),
                        "reasonBullets", Map.of("type", "array", "items", Map.of("type", "string")),
                        "sensorLimitations", Map.of("type", "array", "items", Map.of("type", "string"))));
    }

    private String extractOutputText(JsonNode body) {
        JsonNode outputText = body.get("output_text");
        if (outputText != null && outputText.isTextual()) {
            return outputText.asText();
        }

        StringBuilder builder = new StringBuilder();
        JsonNode output = body.get("output");
        if (output != null && output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.get("content");
                if (content == null || !content.isArray()) {
                    continue;
                }
                for (JsonNode part : content) {
                    String type = part.path("type").asText();
                    JsonNode text = part.get("text");
                    if (text != null && text.isTextual()
                            && ("output_text".equals(type) || "text".equals(type))) {
                        builder.append(text.asText());
                    }
                }
            }
        }

        if (builder.isEmpty()) {
            throw new IllegalStateException("OpenAI response did not contain output_text.");
        }
        return builder.toString();
    }

    private void validateGenerated(JsonNode generated, AiCareSummaryResponse fallback) {
        String rendered = generated.toString();
        List<String> forbidden = FORBIDDEN_CLAIMS.stream()
                .filter(rendered::contains)
                .toList();
        if (!forbidden.isEmpty()) {
            throw new IllegalStateException("LLM output contains forbidden claims: " + String.join(", ", forbidden));
        }

        if ("UNKNOWN".equals(fallback.stressLabels().uvLight())) {
            List<String> limitations = requiredTextArray(generated, "sensorLimitations");
            boolean mentionsUvLimitation = limitations.stream()
                    .anyMatch(item -> item.contains("UV") || item.contains("light") || item.contains("빛"));
            if (!mentionsUvLimitation) {
                throw new IllegalStateException("LLM output must mention UV/light limitation when uvLight is UNKNOWN.");
            }
        }
    }

    private String requiredText(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalStateException("OpenAI output field is missing: " + fieldName);
        }
        return value.asText();
    }

    private List<String> requiredTextArray(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || !value.isArray()) {
            throw new IllegalStateException("OpenAI output field is missing: " + fieldName);
        }

        List<String> result = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isTextual()) {
                throw new IllegalStateException("OpenAI output array contains non-string value: " + fieldName);
            }
            result.add(item.asText());
        }
        return result;
    }

    private String sanitizeError(String message) {
        if (message == null || message.isBlank()) {
            return "OpenAI generation failed.";
        }
        return message.length() > 300 ? message.substring(0, 300) : message;
    }

    private record GeneratedExplanation(
            String shortText,
            List<String> reasonBullets,
            List<String> sensorLimitations
    ) {
    }
}
