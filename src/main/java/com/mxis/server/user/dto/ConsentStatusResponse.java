package com.mxis.server.user.dto;

import com.mxis.server.common.enums.ConsentType;
import java.time.LocalDateTime;

public record ConsentStatusResponse(
        ConsentType consentType,
        boolean agreed,
        String termsVersion,
        LocalDateTime occurredAt
) {
}
