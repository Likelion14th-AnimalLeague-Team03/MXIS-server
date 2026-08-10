package com.mxis.server.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ConsentUpdateRequest(
        @NotEmpty @Valid List<ConsentItem> consents
) {
}
