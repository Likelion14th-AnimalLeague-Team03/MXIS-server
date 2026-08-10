package com.mxis.server.device.dto;

import com.mxis.server.common.enums.DeviceConnectionStatus;
import com.mxis.server.device.entity.Device;
import java.time.LocalDateTime;

public record DeviceResponse(
        Long id,
        String serialNumber,
        String deviceName,
        String macAddress,
        String firmwareVersion,
        Integer batteryLevel,
        DeviceConnectionStatus connectionStatus,
        LocalDateTime lastSyncedAt,
        LocalDateTime registeredAt
) {
    public static DeviceResponse from(Device device) {
        return new DeviceResponse(
                device.getId(),
                device.getSerialNumber(),
                device.getDeviceName(),
                device.getMacAddress(),
                device.getFirmwareVersion(),
                device.getBatteryLevel(),
                device.getConnectionStatus(),
                device.getLastSyncedAt(),
                device.getRegisteredAt());
    }
}
