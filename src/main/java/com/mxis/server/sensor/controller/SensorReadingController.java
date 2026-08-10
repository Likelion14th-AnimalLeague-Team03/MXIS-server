package com.mxis.server.sensor.controller;

import com.mxis.server.common.response.ApiResponse;
import com.mxis.server.common.security.UserPrincipal;
import com.mxis.server.sensor.dto.SensorReadingBatchRequest;
import com.mxis.server.sensor.dto.SensorReadingBatchResponse;
import com.mxis.server.sensor.service.SensorReadingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices/{deviceId}/sensor-readings")
@RequiredArgsConstructor
public class SensorReadingController {

    private final SensorReadingService sensorReadingService;

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SensorReadingBatchResponse> syncBatch(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long deviceId,
            @Valid @RequestBody SensorReadingBatchRequest request) {
        return ApiResponse.ok(sensorReadingService.syncBatch(principal.userId(), deviceId, request));
    }
}
