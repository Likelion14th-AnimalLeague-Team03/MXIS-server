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
        List<String> materialSubtypes,
        String color,
        String imageUrl,
        LocalDate purchasedAt,
        LocalDateTime registeredAt
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getDppCode(),
                product.getProductName(),
                product.getModelCode(),
                product.getMaterialId(),
                product.getMaterialSubtypes(),
                product.getColor(),
                product.getImageUrl(),
                product.getPurchasedAt(),
                product.getRegisteredAt());
    }
}
