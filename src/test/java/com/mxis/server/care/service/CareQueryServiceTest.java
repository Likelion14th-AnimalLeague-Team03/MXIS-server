package com.mxis.server.care.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mxis.server.care.dto.SensorSummaryResponse.HumidityPoint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 30D 그래프의 변곡점 추출은 DB와 무관한 순수 알고리즘이라 리포지토리 없이 직접 검증한다.
 * (다른 생성자 인자는 이 메서드가 쓰지 않으므로 전부 null로 넘긴다.)
 */
class CareQueryServiceTest {

    private final CareQueryService service = new CareQueryService(null, null, null, null, null, null);

    private static HumidityPoint point(String date, double value) {
        return new HumidityPoint(LocalDate.parse(date), BigDecimal.valueOf(value));
    }

    @Test
    void extractTurningPoints_keepsOnlyEndsWhenMonotonic() {
        List<HumidityPoint> daily = List.of(
                point("2026-07-01", 30), point("2026-07-02", 32), point("2026-07-03", 35),
                point("2026-07-04", 38), point("2026-07-05", 40));

        assertThat(service.extractTurningPoints(daily))
                .containsExactly(point("2026-07-01", 30), point("2026-07-05", 40));
    }

    @Test
    void extractTurningPoints_keepsPeaksAndValleys() {
        // 30 -> 45(peak) -> 20(valley) -> 50(peak) -> 35
        List<HumidityPoint> daily = List.of(
                point("2026-07-01", 30), point("2026-07-02", 45), point("2026-07-03", 20),
                point("2026-07-04", 50), point("2026-07-05", 35));

        assertThat(service.extractTurningPoints(daily))
                .containsExactly(
                        point("2026-07-01", 30), point("2026-07-02", 45), point("2026-07-03", 20),
                        point("2026-07-04", 50), point("2026-07-05", 35));
    }

    @Test
    void extractTurningPoints_flatRunIsNotATurningPoint() {
        // 30 -> 30(평탄, 변곡점 아님) -> 40
        List<HumidityPoint> daily = List.of(
                point("2026-07-01", 30), point("2026-07-02", 30), point("2026-07-03", 40));

        assertThat(service.extractTurningPoints(daily))
                .containsExactly(point("2026-07-01", 30), point("2026-07-03", 40));
    }

    @Test
    void extractTurningPoints_twoOrFewerPointsReturnedAsIs() {
        List<HumidityPoint> empty = List.of();
        List<HumidityPoint> single = List.of(point("2026-07-01", 30));
        List<HumidityPoint> two = List.of(point("2026-07-01", 30), point("2026-07-02", 40));

        assertThat(service.extractTurningPoints(empty)).isEmpty();
        assertThat(service.extractTurningPoints(single)).isEqualTo(single);
        assertThat(service.extractTurningPoints(two)).isEqualTo(two);
    }
}
