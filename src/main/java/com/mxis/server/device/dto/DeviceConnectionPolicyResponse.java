package com.mxis.server.device.dto;

import com.mxis.server.device.config.DeviceConnectionProperties;
import java.util.List;

public record DeviceConnectionPolicyResponse(
        List<String> allowedServiceUuids,
        int scanTimeoutSeconds,
        int connectTimeoutSeconds
) {
    public static DeviceConnectionPolicyResponse from(DeviceConnectionProperties properties) {
        return new DeviceConnectionPolicyResponse(
                properties.getAllowedServiceUuids(),
                properties.getScanTimeoutSeconds(),
                properties.getConnectTimeoutSeconds());
    }
}
