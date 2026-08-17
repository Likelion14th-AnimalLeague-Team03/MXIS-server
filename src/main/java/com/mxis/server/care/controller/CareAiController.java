package com.mxis.server.care.controller;

import com.mxis.server.care.dto.OpenAiStatusResponse;
import com.mxis.server.care.service.OpenAiExplanationService;
import com.mxis.server.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/care")
@RequiredArgsConstructor
public class CareAiController {

    private final OpenAiExplanationService openAiExplanationService;

    @GetMapping("/openai-status")
    public ApiResponse<OpenAiStatusResponse> getOpenAiStatus() {
        return ApiResponse.ok(openAiExplanationService.getStatus());
    }
}
