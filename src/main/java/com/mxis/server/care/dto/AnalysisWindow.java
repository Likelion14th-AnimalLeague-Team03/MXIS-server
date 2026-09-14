package com.mxis.server.care.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

/** All windows are half-open; calendar buckets never include future measurements. */
public record AnalysisWindow(LocalDateTime from, LocalDateTime to) {
    public static AnalysisWindow rolling(SensorPeriod period, LocalDateTime now) {
        return new AnalysisWindow(now.minusDays(period.days()), now);
    }

    public static AnalysisWindow environment(SensorPeriod period, LocalDateTime now) {
        LocalDate start = period.isYear()
                ? YearMonth.from(now).minusMonths(11).atDay(1)
                : now.toLocalDate().minusDays(period.days() - 1L);
        return new AnalysisWindow(start.atStartOfDay(), now);
    }
}
