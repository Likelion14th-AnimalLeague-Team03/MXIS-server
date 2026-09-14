package com.mxis.server.sensor.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SensorReadingItem(
        @NotNull @PositiveOrZero Long sequenceNumber,
        @DecimalMin("-273.15") @Digits(integer = 3, fraction = 2) BigDecimal temperature,
        @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal humidity,
        @PositiveOrZero @Digits(integer = 3, fraction = 3) BigDecimal maxShockLevel,
        @PositiveOrZero Integer motionCount,
        boolean isOuting,
        @NotNull LocalDateTime measuredAt
) {
    public SensorReadingItem {
        // MariaDB DATETIME(6) preserves microseconds; normalize before comparing replays.
        if (measuredAt != null) {
            measuredAt = measuredAt.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        }
    }
}
