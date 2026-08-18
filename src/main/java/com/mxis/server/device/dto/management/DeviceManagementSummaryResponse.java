package com.mxis.server.device.dto.management;

import com.mxis.server.care.dto.ScreenProductSummary;
import com.mxis.server.common.enums.DeviceConnectionStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record DeviceManagementSummaryResponse(
        List<ProductImage> products,
        ScreenProductSummary primaryProduct,
        long totalOutingCount,
        PrimaryDevice primaryDevice,
        CurrentEnvironment currentEnvironment
) {
    public record ProductImage(Long productId, String productImageUrl) {
    }

    public record PrimaryDevice(
            Long deviceId,
            String serialNumber,
            String deviceImageUrl,
            DeviceConnectionStatus connectionStatus,
            Integer batteryLevel,
            LocalDateTime lastSyncedAt
    ) {
    }

    public record CurrentEnvironment(
            BigDecimal temperature,
            BigDecimal humidity,
            String measuredAt
    ) {
    }
}
