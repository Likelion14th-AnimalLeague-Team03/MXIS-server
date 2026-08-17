package com.mxis.server.care.dto;

import com.mxis.server.product.entity.Product;

public record ScreenProductSummary(
        Long productId,
        String productImageUrl,
        String productName,
        String materialId,
        String materialDisplayName,
        String color,
        String modelCode,
        String dppCode
) {
    public static ScreenProductSummary from(Product product) {
        return new ScreenProductSummary(
                product.getId(),
                product.getProductImageUrl(),
                product.getProductName(),
                product.getMaterialId(),
                product.getMaterialDisplayName(),
                product.getColor(),
                product.getModelCode(),
                product.getDppCode());
    }
}
