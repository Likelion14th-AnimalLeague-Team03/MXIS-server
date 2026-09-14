package com.mxis.server.care.service;

import com.mxis.server.care.config.OpenAiProperties;
import com.mxis.server.care.dto.OpenAiStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Compatibility status endpoint for the retired Java LLM writer. Python owns copy generation. */
@Service
@RequiredArgsConstructor
public class OpenAiExplanationService {
    private final OpenAiProperties properties;

    public OpenAiStatusResponse getStatus() {
        return new OpenAiStatusResponse(false, properties.hasApiKey(), properties.getModel(), properties.getTimeoutSeconds());
    }
}
