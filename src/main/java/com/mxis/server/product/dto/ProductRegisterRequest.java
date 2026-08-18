package com.mxis.server.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record ProductRegisterRequest(
        @Size(max = 100) String dppCode,
        @NotBlank @Size(max = 100) String productName,
        @Size(max = 50) String modelCode,
        @NotBlank @Size(max = 50) String materialId,
        @Size(max = 100) String materialDisplayName,
        List<@Size(max = 50) String> materialSubtypes,
        @Size(max = 30) String color,
        @Size(max = 500) String productImageUrl,
        @Size(max = 500) String productThumbnailUrl,
        LocalDate purchasedAt
) {
}
