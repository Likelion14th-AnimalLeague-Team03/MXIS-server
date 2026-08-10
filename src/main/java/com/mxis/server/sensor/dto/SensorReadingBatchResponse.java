package com.mxis.server.sensor.dto;

import java.time.LocalDateTime;

public record SensorReadingBatchResponse(
        int receivedCount,
        int savedCount,
        int duplicateCount,
        LocalDateTime lastSyncedAt
) {
}
