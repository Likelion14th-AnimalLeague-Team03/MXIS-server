package com.mxis.server.product.dto;

import com.mxis.server.common.enums.ProductDeviceRole;
import com.mxis.server.product.entity.ProductDevice;
import java.time.LocalDateTime;

public record ProductDeviceResponse(
        Long id,
        Long deviceId,
        String serialNumber,
        String deviceName,
        ProductDeviceRole role,
        LocalDateTime attachedAt,
        LocalDateTime detachedAt
) {
    public static ProductDeviceResponse from(ProductDevice pd) {
        return new ProductDeviceResponse(
                pd.getId(),
                pd.getDevice().getId(),
                pd.getDevice().getSerialNumber(),
                pd.getDevice().getDeviceName(),
                pd.getRole(),
                pd.getAttachedAt(),
                pd.getDetachedAt());
    }
}
