package com.mxis.server.care.service;

import static org.assertj.core.api.Assertions.assertThat;
import com.mxis.server.care.dto.AnalysisWindow;
import com.mxis.server.care.dto.SensorPeriod;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AnalysisWindowTest {
    @Test void calendarBucketsUseOneEndTimeAndExcludeFutureMeasurements() {
        LocalDateTime now = LocalDateTime.parse("2026-09-14T12:00:00");
        assertThat(AnalysisWindow.environment(SensorPeriod.SEVEN_DAYS, now).from()).isEqualTo("2026-09-08T00:00:00");
        assertThat(AnalysisWindow.environment(SensorPeriod.THIRTY_DAYS, now).from()).isEqualTo("2026-08-16T00:00:00");
        assertThat(AnalysisWindow.environment(SensorPeriod.ONE_YEAR, now).from()).isEqualTo("2025-10-01T00:00:00");
        for (SensorPeriod period : SensorPeriod.values()) {
            assertThat(AnalysisWindow.environment(period, now).to()).isEqualTo(now);
        }
    }
}
