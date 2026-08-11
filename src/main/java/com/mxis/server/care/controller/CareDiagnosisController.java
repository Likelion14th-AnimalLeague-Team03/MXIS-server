package com.mxis.server.care.controller;

import com.mxis.server.care.dto.CareDashboardResponse;
import com.mxis.server.care.dto.CareReportResponse;
import com.mxis.server.care.dto.CareSuggestionResponse;
import com.mxis.server.care.dto.SensorPeriod;
import com.mxis.server.care.dto.SensorSummaryResponse;
import com.mxis.server.care.service.CareQueryService;
import com.mxis.server.care.service.CareSuggestionService;
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

/** 제품에 종속된 진단·제안 조회 엔드포인트. */
@RestController
@RequestMapping("/api/v1/products/{id}")
@RequiredArgsConstructor
public class CareDiagnosisController {

    private final CareQueryService careQueryService;
    private final CareSuggestionService careSuggestionService;

    @GetMapping("/care-dashboard")
    public ApiResponse<CareDashboardResponse> getCareDashboard(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ApiResponse.ok(careQueryService.getDashboard(principal.userId(), id));
    }

    @GetMapping("/care-reports/latest")
    public ApiResponse<CareReportResponse> getLatestReport(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ApiResponse.ok(careQueryService.getLatestReport(principal.userId(), id));
    }

    @GetMapping("/sensor-summary")
    public ApiResponse<SensorSummaryResponse> getSensorSummary(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @RequestParam String period) {
        SensorPeriod parsed = SensorPeriod.fromCode(period);
        if (parsed == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "period는 7D·30D·1Y 중 하나여야 합니다.");
        }
        return ApiResponse.ok(careQueryService.getSensorSummary(principal.userId(), id, parsed));
    }

    @GetMapping("/care-suggestions/active")
    public ApiResponse<CareSuggestionResponse> getActiveSuggestion(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ApiResponse.ok(careSuggestionService.getActive(principal.userId(), id));
    }
}
