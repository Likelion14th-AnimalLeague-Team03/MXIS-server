package com.mxis.server.care.dto;

import com.mxis.server.common.enums.CareConditionGrade;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 최신 상태 리포트. 필드별 집계 기간이 다르다:
 * 평균 온·습도는 저장된 30일 스냅샷, 건조노출(7일)·함께한시간은 조회 시점 실시간 계산.
 */
public record CareReportResponse(
        CareConditionGrade conditionGrade,
        String conditionSummary,
        String conditionDescription,
        EnvironmentSummary environmentSummary,
        UsagePattern usagePattern,
        String recommendationText,
        LocalDateTime createdAt
) {
    public record EnvironmentSummary(
            PeriodMeasure avgHumidity,
            PeriodMeasure avgTemperature,
            DryExposure dryExposure) {
    }

    public record PeriodMeasure(BigDecimal value, String period, String note) {
    }

    public record DryExposure(String period, String label) {
    }

    public record UsagePattern(String timeTogether, int outingCount30d, int strongShockCount30d) {
    }
}
