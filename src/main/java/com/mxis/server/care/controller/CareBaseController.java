package com.mxis.server.care.controller;

import com.mxis.server.care.dto.AiCareSummaryResponse;
import com.mxis.server.care.dto.CareEnvironmentResponse;
import com.mxis.server.care.dto.SensorPeriod;
import com.mxis.server.care.service.CareQueryService;
import com.mxis.server.common.exception.BusinessException;
import com.mxis.server.common.exception.ErrorCode;
import com.mxis.server.common.response.ApiResponse;
import com.mxis.server.common.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/care/products/{productId}")
@RequiredArgsConstructor
public class CareBaseController {

    private final CareQueryService careQueryService;

    @GetMapping("/summary")
    public ApiResponse<AiCareSummaryResponse> getAiCareSummary(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long productId,
            @RequestParam(defaultValue = "7D") String period) {
        SensorPeriod parsed = parsePeriod(period);
        return ApiResponse.ok(careQueryService.getAiCareSummary(principal.userId(), productId, parsed));
    }

    @GetMapping("/environment")
    public ApiResponse<CareEnvironmentResponse> getCareEnvironment(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long productId,
            @RequestParam(defaultValue = "7D") String period) {
        SensorPeriod parsed = parsePeriod(period);
        return ApiResponse.ok(careQueryService.getCareEnvironment(principal.userId(), productId, parsed));
    }

    private SensorPeriod parsePeriod(String period) {
        SensorPeriod parsed = SensorPeriod.fromCode(period);
        if (parsed == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "period는 7D·30D·1Y 중 하나여야 합니다.");
        }
        return parsed;
    }
}
