package com.mxis.server.care.dto;

import java.math.BigDecimal;

public record CareDiagnosisHomeResponse(
        ScreenProductSummary product,
        long totalOutingCount,
        ConditionSummary condition,
        Environment30d environment30d
) {
    public record ConditionSummary(String summary, String description) {
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
