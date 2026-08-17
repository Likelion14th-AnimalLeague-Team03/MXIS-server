package com.mxis.server.care.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CareEnvironmentResponse(
        Long productId,
        SensorPeriod period,
        LocalDateTime generatedAt,
        AiCareSummaryResponse.DataSufficiency dataSufficiency,
        EnvironmentSummary environmentSummary,
        List<EnvironmentPoint> points,
        EnvironmentCopy copy
) {
    public record EnvironmentSummary(
            BigDecimal avgTemperature,
            BigDecimal avgHumidity,
            String humidityStress,
            String temperatureHeatStress,
            String drynessStress,
            String handlingStress,
            String uvLightStress
    ) {
    }

    public record EnvironmentPoint(
            String label,
            LocalDate from,
            LocalDate to,
            BigDecimal avgTemperature,
            BigDecimal avgHumidity,
            long readingCount
    ) {
    }

    public record EnvironmentCopy(
            @JsonProperty("short")
            String shortText,
            List<String> bullets
    ) {
    }
}
