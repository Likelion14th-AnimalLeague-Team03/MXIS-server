package com.mxis.server.care.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.mxis.server.care.entity.CareAlgorithm;
import com.mxis.server.care.entity.CareReport;
import com.mxis.server.care.entity.CareSuggestion;
import com.mxis.server.care.repository.CareAlgorithmRepository;
import com.mxis.server.care.repository.CareReportRepository;
import com.mxis.server.care.repository.CareSuggestionRepository;
import com.mxis.server.common.enums.CareConditionGrade;
import com.mxis.server.notification.service.NotificationService;
import com.mxis.server.product.entity.Product;
import com.mxis.server.sensor.dto.SensorAggregate;
import com.mxis.server.sensor.entity.SensorReading;
import com.mxis.server.sensor.repository.SensorReadingRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 센서 동기화 직후 진단 리포트(와 필요 시 케어 제안)를 새로 생성한다.
 *
 * 문구(summary/analysis/recommendation, message/reason)는 아직 LLM을 호출하지 않고 규칙 엔진의
 * 고정 폴백 문구를 저장한다. AI 연동 시 이 지점만 교체하면 되며, 호출 실패 시에도 지금 값이 남는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CareDiagnosisService {

    /** 리포트 스냅샷의 분석 대상 기간 (일). */
    static final int REPORT_PERIOD_DAYS = 30;

    private final CareAlgorithmRepository careAlgorithmRepository;
    private final CareReportRepository careReportRepository;
    private final CareSuggestionRepository careSuggestionRepository;
    private final SensorReadingRepository sensorReadingRepository;
    private final CareRuleEngine ruleEngine;
    private final NotificationService notificationService;
    private final MxisAiClient mxisAiClient;

    /**
     * 진단 재계산. 센서 동기화 트랜잭션 안에서 호출되므로, 진단이 불가능한 상황(활성 알고리즘 없음,
     * 측정 데이터 없음)에서는 예외를 던지지 않고 조용히 건너뛴다 - 동기화 자체는 성공해야 한다.
     */
    @Transactional
    public void regenerate(Product product) {
        CareAlgorithm algorithm = careAlgorithmRepository.findByIsActiveTrue().orElse(null);
        if (algorithm == null) {
            log.warn("활성 care_algorithm이 없어 진단을 건너뜁니다. productId={}", product.getId());
            return;
        }

        LocalDateTime periodEnd = LocalDateTime.now();
        LocalDateTime periodStart = periodEnd.minusDays(REPORT_PERIOD_DAYS);
        if (mxisAiClient.isEnabled()) {
            if (regenerateWithAiService(product, algorithm, periodStart, periodEnd)) {
                return;
            }
        }

        SensorAggregate stats = sensorReadingRepository.aggregate(
                product.getId(), periodStart, periodEnd,
                CareRuleEngine.DRY_THRESHOLD, CareRuleEngine.STRONG_SHOCK_THRESHOLD);

        if (stats.isEmpty()) {
            log.debug("분석 기간 내 센서 데이터가 없어 진단을 건너뜁니다. productId={}", product.getId());
            return;
        }

        BigDecimal avgHumidity = scale(stats.avgHumidity());
        BigDecimal avgTemperature = scale(stats.avgTemperature());
        int shockCount = stats.shockCountAsInt();
        int outingCount = (int) sensorReadingRepository.countOutingSessions(
                product.getId(), periodStart, periodEnd);

        CareConditionGrade grade = ruleEngine.conditionGrade(
                ruleEngine.humidityGrade(avgHumidity), ruleEngine.shockGrade(shockCount));

        CareReport report = careReportRepository.save(new CareReport(
                product,
                algorithm,
                grade,
                ruleEngine.summaryText(grade),
                ruleEngine.analysisText(grade),
                ruleEngine.recommendationText(grade),
                periodStart,
                periodEnd,
                avgTemperature,
                stats.maxTemperature(),
                stats.minTemperature(),
                avgHumidity,
                outingCount,
                shockCount));

        if (ruleEngine.needsSuggestion(grade)) {
            createSuggestion(product, report, grade);
        }
    }

    private void createSuggestion(Product product, CareReport report, CareConditionGrade grade) {
        // 제안이 계속 쌓이면 "활성 제안"이 모호해지므로 이전 활성 제안은 만료 처리한다.
        List<CareSuggestion> previous = careSuggestionRepository.findActiveByProductId(product.getId());
        previous.forEach(CareSuggestion::expire);

        LocalDate visitFrom = LocalDate.now().plusDays(4);
        LocalDate visitTo = visitFrom.plusMonths(1);

        CareSuggestion suggestion = careSuggestionRepository.save(new CareSuggestion(
                report,
                product,
                ruleEngine.suggestionMessage(grade),
                ruleEngine.suggestionReason(grade),
                ruleEngine.recommendedService(grade),
                visitFrom,
                visitTo,
                visitTo.atTime(23, 59, 59)));
        notificationService.createCareTimingNotificationIfNeeded(suggestion);
    }

    private boolean regenerateWithAiService(Product product, CareAlgorithm algorithm,
                                            LocalDateTime periodStart, LocalDateTime periodEnd) {
        try {
            List<SensorReading> readings = sensorReadingRepository
                    .findByProductIdAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(
                            product.getId(), periodStart, periodEnd);
            if (readings.isEmpty()) {
                log.debug("분석 기간 내 센서 데이터가 없어 AI 진단을 건너뜁니다. productId={}", product.getId());
                return true;
            }

            MxisAiClient.CareSummaryResult ai = mxisAiClient.getCareSummaryResult(
                    product,
                    readings.get(readings.size() - 1).getDevice().getId(),
                    com.mxis.server.care.dto.SensorPeriod.THIRTY_DAYS,
                    readings);

            SensorAggregate stats = sensorReadingRepository.aggregate(
                    product.getId(), periodStart, periodEnd,
                    CareRuleEngine.DRY_THRESHOLD, CareRuleEngine.STRONG_SHOCK_THRESHOLD);
            BigDecimal avgHumidity = scale(stats.avgHumidity());
            BigDecimal avgTemperature = scale(stats.avgTemperature());
            int shockCount = stats.shockCountAsInt();
            int outingCount = (int) sensorReadingRepository.countOutingSessions(
                    product.getId(), periodStart, periodEnd);
            CareConditionGrade grade = gradeFromAi(ai.aiCareSummary());

            CareReport report = careReportRepository.save(new CareReport(
                    product,
                    algorithm,
                    grade,
                    summaryText(ai.aiCareSummary()),
                    analysisText(ai.aiCareSummary()),
                    recommendationText(ai.aiCareSummary()),
                    periodStart,
                    periodEnd,
                    avgTemperature,
                    stats.maxTemperature(),
                    stats.minTemperature(),
                    avgHumidity,
                    outingCount,
                    shockCount,
                    ai.rawJson()));

            if (needsSuggestion(ai.aiCareSummary(), grade)) {
                createSuggestion(product, report, grade);
            }
            return true;
        } catch (RuntimeException ex) {
            log.warn("AI service 기반 care_report 생성 실패. Java rule fallback으로 전환합니다. productId={}",
                    product.getId(), ex);
            return false;
        }
    }

    private CareConditionGrade gradeFromAi(JsonNode aiCareSummary) {
        String inspectionNeed = aiCareSummary.path("careDecision").path("inspectionNeed").asText("NONE");
        String careNeed = aiCareSummary.path("careDecision").path("careNeed").asText("LOW");
        String label = aiCareSummary.path("productCondition").path("label").asText("");
        if ("REQUIRED".equals(inspectionNeed) || "HIGH".equals(careNeed)) {
            return CareConditionGrade.EXPERT_CHECK;
        }
        if ("CONDITIONAL".equals(inspectionNeed) || "MEDIUM_HIGH".equals(careNeed)
                || "Needs Attention".equals(label)) {
            return CareConditionGrade.LIGHT_CARE;
        }
        if ("MEDIUM".equals(careNeed) || "LOW_MEDIUM".equals(careNeed) || "Standard".equals(label)) {
            return CareConditionGrade.BALANCED;
        }
        return CareConditionGrade.STABLE;
    }

    private String summaryText(JsonNode aiCareSummary) {
        return firstText(
                aiCareSummary.path("llmCopy").path("diagnosisHome").path("short"),
                aiCareSummary.path("explanation").path("short"),
                aiCareSummary.path("productCondition").path("summary"),
                "제품 상태 분석 결과입니다.");
    }

    private String analysisText(JsonNode aiCareSummary) {
        String shortText = firstText(
                aiCareSummary.path("llmCopy").path("careReport").path("short"),
                aiCareSummary.path("explanation").path("short"),
                null);
        List<String> bullets = stringList(firstArray(
                aiCareSummary.path("llmCopy").path("careReport").path("reasonBullets"),
                aiCareSummary.path("explanation").path("reasonBullets")));
        if (shortText == null && bullets.isEmpty()) {
            return "AI 분석 결과를 기반으로 최근 관리 상태를 안내합니다.";
        }
        if (bullets.isEmpty()) {
            return shortText;
        }
        return shortText == null ? String.join(" ", bullets) : shortText + " " + String.join(" ", bullets);
    }

    private String recommendationText(JsonNode aiCareSummary) {
        return firstText(
                aiCareSummary.path("llmCopy").path("careGuide").path("weeklyTip"),
                aiCareSummary.path("reservationCta").path("description"),
                aiCareSummary.path("explanation").path("short"),
                "현재 상태에 맞는 관리 습관을 유지해 주세요.");
    }

    private boolean needsSuggestion(JsonNode aiCareSummary, CareConditionGrade grade) {
        String inspectionNeed = aiCareSummary.path("careDecision").path("inspectionNeed").asText("NONE");
        String careNeed = aiCareSummary.path("careDecision").path("careNeed").asText("LOW");
        return "REQUIRED".equals(inspectionNeed)
                || "CONDITIONAL".equals(inspectionNeed)
                || "MEDIUM_HIGH".equals(careNeed)
                || "HIGH".equals(careNeed)
                || ruleEngine.needsSuggestion(grade);
    }

    private JsonNode firstArray(JsonNode first, JsonNode second) {
        if (first != null && first.isArray()) {
            return first;
        }
        return second;
    }

    private List<String> stringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(node.spliterator(), false)
                .filter(JsonNode::isTextual)
                .map(JsonNode::asText)
                .toList();
    }

    private String firstText(JsonNode first, JsonNode second, String fallback) {
        return firstText(first, second, null, fallback);
    }

    private String firstText(JsonNode first, JsonNode second, JsonNode third, String fallback) {
        JsonNode[] nodes = {first, second, third};
        for (JsonNode node : nodes) {
            if (node != null && node.isTextual() && !node.asText().isBlank()) {
                return node.asText();
            }
        }
        return fallback;
    }

    private static BigDecimal scale(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
