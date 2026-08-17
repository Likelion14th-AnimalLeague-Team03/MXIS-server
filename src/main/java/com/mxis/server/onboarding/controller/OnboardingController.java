package com.mxis.server.onboarding.controller;

import com.mxis.server.common.response.ApiResponse;
import com.mxis.server.common.security.UserPrincipal;
import com.mxis.server.onboarding.dto.OnboardingProductResponse;
import com.mxis.server.onboarding.service.OnboardingService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;

    @GetMapping("/products")
    public ApiResponse<List<OnboardingProductResponse>> getProducts(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(onboardingService.getProducts(principal.userId()));
    }
}
