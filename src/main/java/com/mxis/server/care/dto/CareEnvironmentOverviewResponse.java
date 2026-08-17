package com.mxis.server.care.dto;

import java.math.BigDecimal;
import java.util.List;

public record CareEnvironmentOverviewResponse(
        PeriodEnvironment sevenDays,
        PeriodEnvironment thirtyDays,
        PeriodEnvironment oneYear
) {
    public record PeriodEnvironment(
            String period,
            List<MetricPoint> temperaturePoints,
            List<MetricPoint> humidityPoints,
            BigDecimal avgTemperature,
            BigDecimal avgHumidity,
            int outingCount,
            int shockCount,
            String interpretation
    ) {
    }

    public record MetricPoint(
            String label,
            BigDecimal value
    ) {
    }
}
