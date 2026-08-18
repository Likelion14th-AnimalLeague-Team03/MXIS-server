package com.mxis.server.care.dto;

import java.util.List;

public record CareGuideResponse(
        Long productId,
        String materialId,
        String materialDisplayName,
        String careType,
        String guideImageUrl,
        String title,
        String description,
        List<String> steps,
        String tip
) {
}
