package com.mxis.server.onboarding.dto;

import com.mxis.server.product.entity.Product;

public record OnboardingProductResponse(
        Long productId,
        String productImageUrl,
        String productName,
        String materialId,
        String materialDisplayName,
        String color,
        String modelCode,
        String dppCode,
        boolean isPrimary
) {
    public static OnboardingProductResponse from(Product product, boolean isPrimary) {
        return new OnboardingProductResponse(
                product.getId(),
                product.getProductImageUrl(),
                product.getProductName(),
                product.getMaterialId(),
                product.getMaterialDisplayName(),
                product.getColor(),
                product.getModelCode(),
                product.getDppCode(),
                isPrimary);
    }
}
