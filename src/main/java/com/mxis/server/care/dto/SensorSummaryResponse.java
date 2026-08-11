package com.mxis.server.care.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 환경 데이터 상세. care_reports를 거치지 않고 sensor_readings를 직접 집계하는 라이브 응답이며
 * 저장하지 않는다. 7D/30D는 합계(outingCount/shockCount), 1Y는 월평균(...MonthlyAvg)만 채워지고
 * 나머지는 null로 빠진다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SensorSummaryResponse(
        SensorPeriod period,
        List<HumidityPoint> humidityTrend,
        BigDecimal avgTemperature,
        BigDecimal avgHumidity,
        Integer outingCount,
        Integer shockCount,
        Integer outingCountMonthlyAvg,
        Integer shockCountMonthlyAvg,
        String comparisonText,
        boolean insufficientHistory
) {
    public record HumidityPoint(LocalDate date, BigDecimal value) {
    }
}
