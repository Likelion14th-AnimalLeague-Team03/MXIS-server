package com.mxis.server.product.dto;

import java.util.List;

public record ProductRecognizeResponse(
        String dppCode,
        String productName,
        String modelCode,
        String materialId,
        String materialDisplayName,
        List<String> materialSubtypes,
        String color,
        String productImageUrl
) {
}
