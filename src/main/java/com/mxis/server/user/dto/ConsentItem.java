package com.mxis.server.user.dto;

import com.mxis.server.common.enums.ConsentAction;
import com.mxis.server.common.enums.ConsentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConsentItem(
        @NotNull ConsentType consentType,
        @NotNull ConsentAction action,
        @NotBlank String termsVersion
) {
}
