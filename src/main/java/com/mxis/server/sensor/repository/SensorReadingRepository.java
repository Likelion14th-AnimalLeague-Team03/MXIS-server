package com.mxis.server.sensor.repository;

import com.mxis.server.sensor.dto.SensorAggregate;
import com.mxis.server.sensor.entity.SensorReading;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SensorReadingRepository extends JpaRepository<SensorReading, Long> {

    @Query("""
            SELECT sr.sequenceNumber FROM SensorReading sr
            WHERE sr.device.id = :deviceId AND sr.sequenceNumber IN :sequenceNumbers
            """)
    List<Long> findExistingSequenceNumbers(
            @Param("deviceId") Long deviceId, @Param("sequenceNumbers") List<Long> sequenceNumbers);

    /** 진단·리포트용 기간 집계. from 이상, to 미만. */
    @Query("""
            SELECT new com.mxis.server.sensor.dto.SensorAggregate(
                AVG(sr.temperature),
                MAX(sr.temperature),
                MIN(sr.temperature),
                AVG(sr.humidity),
                COUNT(sr),
                SUM(CASE WHEN sr.humidity < :dryThreshold THEN 1L ELSE 0L END),
                SUM(CASE WHEN sr.maxShockLevel >= :shockThreshold THEN 1L ELSE 0L END))
            FROM SensorReading sr
            WHERE sr.product.id = :productId
              AND sr.measuredAt >= :from AND sr.measuredAt < :to
            """)
    SensorAggregate aggregate(@Param("productId") Long productId,
                              @Param("from") LocalDateTime from,
                              @Param("to") LocalDateTime to,
                              @Param("dryThreshold") BigDecimal dryThreshold,
                              @Param("shockThreshold") BigDecimal shockThreshold);

    /**
     * 외출 "세션" 수. 측정 행 하나하나를 세면 크게 부풀려지므로 외출이 기록된 날짜 수로 근사한다.
     * ponytail: 하루 1회 외출 가정. 연속 측정 구간을 묶는 정식 세션 추론이 필요해지면 그때 바꾼다.
     */
    @Query(value = """
            SELECT COUNT(DISTINCT DATE(measured_at)) FROM sensor_readings
            WHERE product_id = :productId AND is_outing = 1
              AND measured_at >= :from AND measured_at < :to
            """, nativeQuery = true)
    long countOutingSessions(@Param("productId") Long productId,
                             @Param("from") LocalDateTime from,
                             @Param("to") LocalDateTime to);

    /** 그래프용 일별 평균 습도. 각 행은 [java.sql.Date, Number]. */
    @Query(value = """
            SELECT DATE(measured_at) AS day, AVG(humidity) AS avg_humidity
            FROM sensor_readings
            WHERE product_id = :productId AND humidity IS NOT NULL
              AND measured_at >= :from AND measured_at < :to
            GROUP BY DATE(measured_at)
            ORDER BY day
            """, nativeQuery = true)
    List<Object[]> findDailyHumidity(@Param("productId") Long productId,
                                     @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to);

    /**
     * 그래프용 월별(해당 월 1일 기준) 평균 습도. 1년 조회에서 사용. 각 행은 [java.sql.Date, Number].
     * DATE_FORMAT()은 MariaDB에서 문자열을 반환하므로 DATE로 CAST해 findDailyHumidity와 반환 타입을 맞춘다.
     */
    @Query(value = """
            SELECT CAST(DATE_FORMAT(measured_at, '%Y-%m-01') AS DATE) AS month, AVG(humidity) AS avg_humidity
            FROM sensor_readings
            WHERE product_id = :productId AND humidity IS NOT NULL
              AND measured_at >= :from AND measured_at < :to
            GROUP BY month
            ORDER BY month
            """, nativeQuery = true)
    List<Object[]> findMonthlyHumidity(@Param("productId") Long productId,
                                       @Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to);
}
