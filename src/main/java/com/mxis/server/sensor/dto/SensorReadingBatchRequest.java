package com.mxis.server.sensor.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SensorReadingBatchRequest(
        @NotEmpty @Size(max = 1000) @Valid List<@NotNull SensorReadingItem> readings
) {
}
