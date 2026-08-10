package com.mxis.server.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeviceRegisterRequest(
        @NotBlank @Size(max = 100) String serialNumber,
        @Size(max = 50) String deviceName,
        @Size(max = 50) String macAddress,
        @Size(max = 20) String firmwareVersion
) {
}
