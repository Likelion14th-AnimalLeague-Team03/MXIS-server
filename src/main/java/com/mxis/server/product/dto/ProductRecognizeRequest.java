package com.mxis.server.product.dto;

import jakarta.validation.constraints.NotBlank;

public record ProductRecognizeRequest(
        @NotBlank String dppCode
) {
}
