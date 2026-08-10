package com.mxis.server.product.dto;

public record ProductRecognizeResponse(
        String dppCode,
        String productName,
        String modelCode,
        String material,
        String color,
        String imageUrl
) {
}
