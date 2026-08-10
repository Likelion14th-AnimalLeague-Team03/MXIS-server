package com.mxis.server.product.controller;

import com.mxis.server.common.response.ApiResponse;
import com.mxis.server.common.security.UserPrincipal;
import com.mxis.server.product.dto.ProductDeviceLinkRequest;
import com.mxis.server.product.dto.ProductDeviceResponse;
import com.mxis.server.product.service.ProductDeviceService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products/{productId}/devices")
@RequiredArgsConstructor
public class ProductDeviceController {

    private final ProductDeviceService productDeviceService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductDeviceResponse> link(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long productId,
            @Valid @RequestBody ProductDeviceLinkRequest request) {
        return ApiResponse.ok(productDeviceService.link(principal.userId(), productId, request));
    }

    @GetMapping
    public ApiResponse<List<ProductDeviceResponse>> getLinkedDevices(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long productId) {
        return ApiResponse.ok(productDeviceService.getLinkedDevices(principal.userId(), productId));
    }

    @PatchMapping("/{deviceId}")
    public ApiResponse<ProductDeviceResponse> promoteToPrimary(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long productId,
            @PathVariable Long deviceId) {
        return ApiResponse.ok(productDeviceService.promoteToPrimary(principal.userId(), productId, deviceId));
    }

    @DeleteMapping("/{deviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long productId,
            @PathVariable Long deviceId) {
        productDeviceService.unlink(principal.userId(), productId, deviceId);
    }
}
