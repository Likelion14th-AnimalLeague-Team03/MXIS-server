package com.mxis.server.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ProductRegisterRequest(
        @Size(max = 100) String dppCode,
        @NotBlank @Size(max = 100) String productName,
        @Size(max = 50) String modelCode,
        @NotBlank @Size(max = 50) String material,
        @Size(max = 30) String color,
        @Size(max = 500) String imageUrl,
        LocalDate purchasedAt
) {
}
