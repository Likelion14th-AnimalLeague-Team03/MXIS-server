package com.mxis.server.device.controller;

import com.mxis.server.common.response.ApiResponse;
import com.mxis.server.common.security.UserPrincipal;
import com.mxis.server.device.dto.management.DeviceManagementSummaryResponse;
import com.mxis.server.device.service.DeviceManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/device-management")
@RequiredArgsConstructor
public class DeviceManagementController {

    private final DeviceManagementService deviceManagementService;

    @GetMapping("/summary")
    public ApiResponse<DeviceManagementSummaryResponse> getSummary(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(deviceManagementService.getSummary(principal.userId()));
    }
}
