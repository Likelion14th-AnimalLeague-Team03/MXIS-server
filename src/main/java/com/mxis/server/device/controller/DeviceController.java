package com.mxis.server.device.controller;

import com.mxis.server.common.response.ApiResponse;
import com.mxis.server.common.security.UserPrincipal;
import com.mxis.server.device.dto.DeviceConnectionPolicyResponse;
import com.mxis.server.device.dto.DeviceLookupResponse;
import com.mxis.server.device.dto.DeviceRegisterRequest;
import com.mxis.server.device.dto.DeviceResponse;
import com.mxis.server.device.dto.DeviceStatusUpdateRequest;
import com.mxis.server.device.service.DeviceService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DeviceResponse> register(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody DeviceRegisterRequest request) {
        return ApiResponse.ok(deviceService.register(principal.userId(), request));
    }

    @GetMapping
    public ApiResponse<List<DeviceResponse>> getMyDevices(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(deviceService.getMyDevices(principal.userId()));
    }

    @GetMapping("/connection-policy")
    public ApiResponse<DeviceConnectionPolicyResponse> getConnectionPolicy() {
        return ApiResponse.ok(deviceService.getConnectionPolicy());
    }

    @GetMapping("/lookup")
    public ApiResponse<DeviceLookupResponse> lookup(@RequestParam String serialNumber) {
        return ApiResponse.ok(deviceService.lookup(serialNumber));
    }

    @GetMapping("/{id}")
    public ApiResponse<DeviceResponse> getDevice(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ApiResponse.ok(deviceService.getDevice(principal.userId(), id));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<DeviceResponse> updateStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody DeviceStatusUpdateRequest request) {
        return ApiResponse.ok(deviceService.updateStatus(principal.userId(), id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long id) {
        deviceService.delete(principal.userId(), id);
    }
}
