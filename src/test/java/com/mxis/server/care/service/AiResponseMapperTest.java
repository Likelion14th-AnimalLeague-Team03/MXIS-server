package com.mxis.server.care.service;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mxis.server.care.dto.SensorPeriod;
import org.junit.jupiter.api.Test;

class AiResponseMapperTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AiResponseMapper mapper = new AiResponseMapper(json);

    static String validJson() {
        return """
                {"aiCareSummary":{
                  "generatedAt":"2026-09-14T03:00:00Z","analysisWindowDays":30,
                  "dataSufficiency":{"status":"SUFFICIENT","validReadingCount":24,"coverageHours":25},
                  "productCondition":{"label":"Excellent","score":92,"primaryFactor":null,"summary":"안정적입니다."},
                  "stressLabels":{"humidity":"LOW","temperatureHeat":"LOW","dryness":"LOW","handling":"LOW","usageRest":"UNKNOWN","uvLight":"UNKNOWN"},
                  "explanation":{"short":"안정적입니다.","reasonBullets":[],"sensorLimitations":["UV 미측정"]},
                  "careDecision":{"careNeed":"LOW","inspectionNeed":"NONE"},
                  "copyGeneration":{"source":"openai","model":"example"}}}
                """;
    }

    @Test void retainsActualScoreAndConvertsOffsetTimeToSeoul() {
        var result = mapper.read(7L, SensorPeriod.THIRTY_DAYS, validJson());
        assertThat(result.summary().productCondition().score()).isEqualTo(92);
        assertThat(result.summary().generatedAt()).hasHour(12);
        assertThat(result.aiCareSummary().path("careDecision").path("careNeed").asText()).isEqualTo("LOW");
    }

    @Test void rejectsMissingNullAndEmptySummary() {
        for (String body : new String[]{"{}", "null", "{\"aiCareSummary\":null}", "{\"aiCareSummary\":{}}"}) {
            assertThatThrownBy(() -> mapper.read(7L, SensorPeriod.THIRTY_DAYS, body))
                    .isInstanceOf(AiServiceException.class);
        }
    }

    @Test void preservesDataCollectionAndNullScore() throws Exception {
        ObjectNode body = (ObjectNode) json.readTree(validJson());
        ObjectNode summary = (ObjectNode) body.get("aiCareSummary");
        ((ObjectNode) summary.get("dataSufficiency")).put("status", "INSUFFICIENT_DATA");
        ((ObjectNode) summary.get("productCondition")).put("label", "Collecting Data").putNull("score");
        var result = mapper.read(7L, SensorPeriod.THIRTY_DAYS, body.toString());
        assertThat(result.summary().dataSufficiency().status()).isEqualTo("INSUFFICIENT_DATA");
        assertThat(result.summary().productCondition().score()).isNull();
    }

    @Test void rejectsInsufficientResultWithNormalScore() throws Exception {
        ObjectNode body = (ObjectNode) json.readTree(validJson());
        ((ObjectNode) body.path("aiCareSummary").path("dataSufficiency")).put("status", "INSUFFICIENT_DATA");
        assertThatThrownBy(() -> mapper.read(7L, SensorPeriod.THIRTY_DAYS, body.toString()))
                .isInstanceOf(AiServiceException.class);
    }

    @Test void rejectsUnknownDecisionAndNonNumericScore() throws Exception {
        ObjectNode body = (ObjectNode) json.readTree(validJson());
        ((ObjectNode) body.path("aiCareSummary").path("careDecision")).put("careNeed", "NEW_UNKNOWN_VALUE");
        String unknownDecision = body.toString();
        assertThatThrownBy(() -> mapper.read(7L, SensorPeriod.THIRTY_DAYS, unknownDecision)).isInstanceOf(AiServiceException.class);
        body = (ObjectNode) json.readTree(validJson());
        ((ObjectNode) body.path("aiCareSummary").path("productCondition")).put("score", "92");
        String invalidScore = body.toString();
        assertThatThrownBy(() -> mapper.read(7L, SensorPeriod.THIRTY_DAYS, invalidScore)).isInstanceOf(AiServiceException.class);
    }

    @Test void rejectsSufficientResultsBelowTheSharedMinimumAndWrongProduct() throws Exception {
        ObjectNode tooFew = (ObjectNode) json.readTree(validJson());
        ((ObjectNode) tooFew.path("aiCareSummary").path("dataSufficiency")).put("validReadingCount", 1).put("coverageHours", 1);
        assertThatThrownBy(() -> mapper.read(7L, SensorPeriod.THIRTY_DAYS, tooFew.toString())).isInstanceOf(AiServiceException.class);
        ObjectNode wrongProduct = (ObjectNode) json.readTree(validJson());
        ((ObjectNode) wrongProduct.path("aiCareSummary")).put("productId", "999");
        assertThatThrownBy(() -> mapper.read(7L, SensorPeriod.THIRTY_DAYS, wrongProduct.toString())).isInstanceOf(AiServiceException.class);
    }

    @Test void rejectsDifferentAnalysisPeriod() {
        assertThatThrownBy(() -> mapper.read(7L, SensorPeriod.SEVEN_DAYS, validJson()))
                .isInstanceOf(AiServiceException.class).hasMessageContaining("window");
    }
}
