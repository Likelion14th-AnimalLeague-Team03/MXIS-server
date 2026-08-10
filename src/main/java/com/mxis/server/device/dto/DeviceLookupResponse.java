package com.mxis.server.device.dto;

public record DeviceLookupResponse(
        String serialNumber,
        boolean registrable
) {
}
