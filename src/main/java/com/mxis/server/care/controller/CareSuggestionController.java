package com.mxis.server.care.controller;

import com.mxis.server.care.dto.CareSuggestionResponse;
import com.mxis.server.care.service.CareSuggestionService;
import com.mxis.server.common.response.ApiResponse;
import com.mxis.server.common.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/care-suggestions")
@RequiredArgsConstructor
public class CareSuggestionController {

    private final CareSuggestionService careSuggestionService;

    @GetMapping("/{id}")
    public ApiResponse<CareSuggestionResponse> getSuggestion(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ApiResponse.ok(careSuggestionService.getDetail(principal.userId(), id));
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<CareSuggestionResponse.ReadResult> markRead(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ApiResponse.ok(careSuggestionService.markRead(principal.userId(), id));
    }
}
