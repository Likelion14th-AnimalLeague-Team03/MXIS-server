package com.mxis.server.care.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CareReportScreenResponse(
        Long careReportId,
        LocalDateTime generatedAt,
        ConditionReport condition,
        Environment30d environment30d,
        String interpretation,
        boolean careNeeded,
        int careCycleMonths,
        LocalDate nextCareRecommendedAt
) {
    public record ConditionReport(String summary, String detail) {
    }

    public record Environment30d(
            BigDecimal avgTemperature,
            String temperatureDescription,
            BigDecimal avgHumidity,
            String humidityDescription,
            String shockLevelLabel,
            int outingCount
    ) {
    }
}
