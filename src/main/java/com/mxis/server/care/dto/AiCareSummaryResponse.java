package com.mxis.server.care.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiCareSummaryResponse(
        Long productId,
        LocalDateTime generatedAt,
        int analysisWindowDays,
        DataSufficiency dataSufficiency,
        ProductCondition productCondition,
        StressLabels stressLabels,
        Explanation explanation,
        CopyGeneration copyGeneration
) {
    public record DataSufficiency(
            String status,
            String reason,
            long validReadingCount,
            Double coverageHours,
            LocalDateTime lastMeasuredAt,
            LocalDateTime lastSyncedAt
    ) {
    }

    public record ProductCondition(
            String label,
            Integer score,
            String primaryFactor,
            String summary
    ) {
    }

    public record StressLabels(
            String humidity,
            String temperatureHeat,
            String dryness,
            String handling,
            String usageRest,
            String uvLight
    ) {
    }

    public record Explanation(
            @JsonProperty("short")
            String shortText,
            List<String> reasonBullets,
            List<String> sensorLimitations
    ) {
    }

    public record CopyGeneration(
            String source,
            String model,
            String error
    ) {
    }
}
