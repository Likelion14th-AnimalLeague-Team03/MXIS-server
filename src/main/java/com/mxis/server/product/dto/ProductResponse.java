package com.mxis.server.product.dto;

import com.mxis.server.product.entity.Product;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ProductResponse(
        Long id,
        String dppCode,
        String productName,
        String modelCode,
        String material,
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
                product.getMaterial(),
                product.getColor(),
                product.getImageUrl(),
                product.getPurchasedAt(),
                product.getRegisteredAt());
    }
}
