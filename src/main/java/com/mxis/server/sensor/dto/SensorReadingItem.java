package com.mxis.server.sensor.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SensorReadingItem(
        @NotNull Long sequenceNumber,
        BigDecimal temperature,
        BigDecimal humidity,
        BigDecimal maxShockLevel,
        Integer motionCount,
        boolean isOuting,
        @NotNull LocalDateTime measuredAt
) {
}
