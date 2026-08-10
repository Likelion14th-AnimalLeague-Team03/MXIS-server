package com.mxis.server.product.dto;

import com.mxis.server.common.enums.ProductDeviceRole;
import jakarta.validation.constraints.NotNull;

public record ProductDeviceLinkRequest(
        @NotNull Long deviceId,
        ProductDeviceRole role
) {
}
