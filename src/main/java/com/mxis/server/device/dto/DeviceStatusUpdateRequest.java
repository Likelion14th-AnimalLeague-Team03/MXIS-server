package com.mxis.server.device.dto;

import com.mxis.server.common.enums.DeviceConnectionStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record DeviceStatusUpdateRequest(
        DeviceConnectionStatus connectionStatus,
        @Min(0) @Max(100) Integer batteryLevel
) {
}
