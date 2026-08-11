package com.mxis.server.sensor.dto;

import java.math.BigDecimal;

/**
 * 특정 제품/기간의 센서 집계 결과. 해당 기간에 데이터가 없으면 평균/최대/최소는 null, 카운트는 0이다.
 * (JPQL 생성자 표현식으로 채워지므로 필드 타입은 집계 함수 반환 타입과 일치해야 한다:
 *  AVG -> Double, MAX/MIN(BigDecimal 컬럼) -> BigDecimal, COUNT/SUM -> Long)
 */
public record SensorAggregate(
        Double avgTemperature,
        BigDecimal maxTemperature,
        BigDecimal minTemperature,
        Double avgHumidity,
        Long readingCount,
        Long dryReadingCount,
        Long shockCount
) {
    public boolean isEmpty() {
        return readingCount == null || readingCount == 0;
    }

    public int shockCountAsInt() {
        return shockCount == null ? 0 : shockCount.intValue();
    }

    /** 건조(습도 기준치 미만) 측정 비율. 데이터가 없으면 0. */
    public double dryRatio() {
        if (isEmpty() || dryReadingCount == null) {
            return 0.0;
        }
        return (double) dryReadingCount / readingCount;
    }
}
