package com.mxis.server.care.service;

import com.mxis.server.care.dto.AiCareSummaryResponse;
import com.mxis.server.care.dto.SensorPeriod;
import com.mxis.server.common.enums.CareConditionGrade;
import com.mxis.server.sensor.dto.SensorAggregate;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Shared decision policy for snapshots, live fallback, home and care suggestions. */
@Component
@RequiredArgsConstructor
public class CareDecisionPolicy {
    public static final int MIN_VALID_READINGS = 24;
    public static final int MIN_COVERAGE_HOURS = 24;
    private final CareRuleEngine rules;

    public AiCareSummaryResponse.DataSufficiency dataSufficiency(long count, LocalDateTime first,
            LocalDateTime last, LocalDateTime synced, LocalDateTime now) {
        double coverage = first == null || last == null ? 0 : Math.max(0, ChronoUnit.MINUTES.between(first, last) / 60.0);
        String status = "SUFFICIENT";
        String reason = null;
        if (count == 0) {
            status = "NO_DATA";
            reason = "NO_VALID_READING";
        } else if (count < MIN_VALID_READINGS) {
            status = "INSUFFICIENT_DATA";
            reason = "MIN_READING_COUNT_NOT_MET";
        } else if (coverage < MIN_COVERAGE_HOURS) {
            status = "INSUFFICIENT_DATA";
            reason = "MIN_COVERAGE_HOURS_NOT_MET";
        } else if (last == null || last.isBefore(now.minusDays(3))) {
            status = "STALE_DATA";
            reason = "LATEST_READING_STALE";
        }
        return new AiCareSummaryResponse.DataSufficiency(status, reason, count,
                Math.round(coverage * 10.0) / 10.0, last, synced);
    }

    public AiCareSummaryResponse fallback(Long productId, SensorPeriod period, LocalDateTime generatedAt,
            SensorAggregate stats, AiCareSummaryResponse.DataSufficiency sufficiency) {
        boolean sufficient = "SUFFICIENT".equals(sufficiency.status())
                && !stats.isEmpty() && stats.avgHumidity() != null && stats.avgTemperature() != null;
        if (!sufficient && "SUFFICIENT".equals(sufficiency.status())) {
            sufficiency = new AiCareSummaryResponse.DataSufficiency("INSUFFICIENT_DATA", "MISSING_ENVIRONMENT_DATA",
                    sufficiency.validReadingCount(), sufficiency.coverageHours(), sufficiency.lastMeasuredAt(), sufficiency.lastSyncedAt());
        }
        CareConditionGrade grade = sufficient ? rules.conditionGrade(
                rules.humidityGrade(BigDecimal.valueOf(stats.avgHumidity())), rules.shockGrade(stats.shockCountAsInt()))
                : CareConditionGrade.COLLECTING_DATA;
        String humidity = !sufficient ? "UNKNOWN" : switch (rules.humidityGrade(BigDecimal.valueOf(stats.avgHumidity()))) {
            case UNKNOWN -> "UNKNOWN";
            case IDEAL -> "LOW";
            case SLIGHTLY_DRY, SLIGHTLY_HUMID -> "CAUTION";
            case DRY_RISK, HUMID_RISK -> "ELEVATED";
        };
        String handling = !sufficient ? "UNKNOWN" : switch (rules.shockGrade(stats.shockCountAsInt())) {
            case LOW -> "LOW";
            case MEDIUM -> "CAUTION";
            case HIGH -> "ELEVATED";
        };
        String temperature = !sufficient ? "UNKNOWN"
                : stats.avgTemperature() < 15 || stats.avgTemperature() > 28 ? "CAUTION" : "LOW";
        // An adverse temperature must never produce an Excellent fallback condition.
        if (sufficient && ("CAUTION".equals(temperature) || stats.dryRatio() >= 0.2) && grade == CareConditionGrade.STABLE) {
            grade = CareConditionGrade.BALANCED;
        }
        String factor = !sufficient ? null : !"LOW".equals(humidity) ? "humidity"
                : !"LOW".equals(handling) ? "handling" : !"LOW".equals(temperature) ? "temperature_heat" : null;
        String summary = rules.summaryText(grade);
        return new AiCareSummaryResponse(productId, generatedAt, period.days(), sufficiency,
                new AiCareSummaryResponse.ProductCondition(label(grade), fallbackScore(grade), factor, summary),
                new AiCareSummaryResponse.StressLabels(humidity, temperature,
                        !sufficient ? "UNKNOWN" : stats.dryRatio() >= 0.2 ? "CAUTION" : "LOW",
                        handling, "UNKNOWN", "UNKNOWN"),
                new AiCareSummaryResponse.Explanation(summary, List.of(rules.analysisText(grade)),
                        List.of("센서 데이터만으로 제품 표면의 손상 여부를 확정하지 않습니다.")),
                new AiCareSummaryResponse.CopyGeneration("deterministic_fallback", null, null));
    }

    public CareConditionGrade grade(AiCareSummaryResponse summary, String careNeed, String inspectionNeed) {
        if (summary == null || summary.dataSufficiency() == null
                || !"SUFFICIENT".equals(summary.dataSufficiency().status())
                || summary.productCondition() == null || summary.productCondition().score() == null
                || summary.productCondition().label() == null) {
            return CareConditionGrade.COLLECTING_DATA;
        }
        if ("REQUIRED".equals(inspectionNeed) || "HIGH".equals(careNeed)) return CareConditionGrade.EXPERT_CHECK;
        if ("CONDITIONAL".equals(inspectionNeed) || "MEDIUM_HIGH".equals(careNeed)
                || "MEDIUM".equals(careNeed)) return CareConditionGrade.LIGHT_CARE;
        return switch (summary.productCondition().label()) {
            case "Excellent" -> "LOW_MEDIUM".equals(careNeed) ? CareConditionGrade.BALANCED : CareConditionGrade.STABLE;
            case "Standard" -> CareConditionGrade.BALANCED;
            case "Needs Attention" -> CareConditionGrade.LIGHT_CARE;
            default -> CareConditionGrade.COLLECTING_DATA;
        };
    }

    public boolean needsSuggestion(String status, String careNeed, String inspectionNeed, CareConditionGrade grade) {
        return "SUFFICIENT".equals(status) && grade != CareConditionGrade.COLLECTING_DATA
                && ("REQUIRED".equals(inspectionNeed) || "CONDITIONAL".equals(inspectionNeed)
                || "MEDIUM".equals(careNeed) || "MEDIUM_HIGH".equals(careNeed) || "HIGH".equals(careNeed)
                || rules.needsSuggestion(grade));
    }

    public int careCycleMonths(String status, String careNeed, String inspectionNeed, CareConditionGrade grade) {
        if (!"SUFFICIENT".equals(status) || grade == null || grade == CareConditionGrade.COLLECTING_DATA) return 0;
        if ("REQUIRED".equals(inspectionNeed) || "HIGH".equals(careNeed) || grade == CareConditionGrade.EXPERT_CHECK) return 1;
        return needsSuggestion(status, careNeed, inspectionNeed, grade) ? 3 : 6;
    }

    public String fallbackCareNeed(CareConditionGrade grade) {
        return switch (grade) {
            case COLLECTING_DATA -> "UNKNOWN";
            case STABLE -> "LOW";
            case BALANCED -> "LOW_MEDIUM";
            case LIGHT_CARE -> "MEDIUM";
            case EXPERT_CHECK -> "HIGH";
        };
    }

    private String label(CareConditionGrade grade) {
        return switch (grade) {
            case COLLECTING_DATA -> "Collecting Data";
            case STABLE -> "Excellent";
            case BALANCED -> "Standard";
            case LIGHT_CARE, EXPERT_CHECK -> "Needs Attention";
        };
    }

    private Integer fallbackScore(CareConditionGrade grade) {
        return switch (grade) {
            case COLLECTING_DATA -> null;
            case STABLE -> 100;
            case BALANCED -> 75;
            case LIGHT_CARE -> 50;
            case EXPERT_CHECK -> 25;
        };
    }
}
