package com.mxis.server.care.dto;

import com.mxis.server.common.enums.CareConditionGrade;
import com.mxis.server.common.enums.DeviceConnectionStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 케어진단 홈. 충격은 정성 라벨만 노출하고 원시 횟수는 내려주지 않는다. */
public record CareDashboardResponse(
        ProductSummary product,
        DeviceSummary device,
        CareConditionGrade conditionGrade,
        String conditionSummary,
        String conditionDescription,
        EnvironmentSummary environmentSummary,
        ActiveSuggestion activeSuggestion
) {
    public record ProductSummary(
            Long id, String productName, String materialId, String materialDisplayName, String color, String productImageUrl) {
    }

    /** 대표 센서(PRIMARY_SENSOR) 기기의 상태. 연결된 기기가 없으면 필드가 모두 null. */
    public record DeviceSummary(DeviceConnectionStatus connectionStatus, LocalDateTime lastSyncedAt) {
    }

    public record EnvironmentSummary(
            String periodLabel,
            Measure temperature,
            Measure humidity,
            int outingCount30d,
            String shockLevelLabel) {
    }

    public record Measure(BigDecimal value, String label) {
    }

    public record ActiveSuggestion(Long id, String message, String reasonSummary) {
    }
}
