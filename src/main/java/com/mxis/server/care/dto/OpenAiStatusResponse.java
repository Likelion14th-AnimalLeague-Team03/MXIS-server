package com.mxis.server.care.dto;

public record OpenAiStatusResponse(
        boolean enabled,
        boolean apiKeyConfigured,
        String model,
        int timeoutSeconds
) {
}
