package com.mxis.server.product.dto;

import com.mxis.server.product.entity.Product;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ProductResponse(
        Long id,
        String dppCode,
        String productName,
        String modelCode,
        String materialId,
        String materialDisplayName,
        List<String> materialSubtypes,
        String color,
        String productImageUrl,
        LocalDate purchasedAt,
        LocalDateTime registeredAt,
        boolean isPrimary
) {
    public static ProductResponse from(Product product) {
        return from(product, false);
    }

    public static ProductResponse from(Product product, boolean isPrimary) {
        return new ProductResponse(
                product.getId(),
                product.getDppCode(),
                product.getProductName(),
                product.getModelCode(),
                product.getMaterialId(),
                product.getMaterialDisplayName(),
                product.getMaterialSubtypes(),
                product.getColor(),
                product.getProductImageUrl(),
                product.getPurchasedAt(),
                product.getRegisteredAt(),
                isPrimary);
    }
}
