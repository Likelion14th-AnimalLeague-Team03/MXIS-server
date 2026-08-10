package com.mxis.server.user.controller;

import com.mxis.server.common.response.ApiResponse;
import com.mxis.server.common.security.UserPrincipal;
import com.mxis.server.user.dto.ConsentStatusResponse;
import com.mxis.server.user.dto.ConsentUpdateRequest;
import com.mxis.server.user.dto.NotificationSettingResponse;
import com.mxis.server.user.dto.NotificationSettingUpdateRequest;
import com.mxis.server.user.dto.UserResponse;
import com.mxis.server.user.service.ConsentService;
import com.mxis.server.user.service.NotificationSettingService;
import com.mxis.server.user.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final ConsentService consentService;
    private final NotificationSettingService notificationSettingService;

    @GetMapping
    public ApiResponse<UserResponse> getMe(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(userService.getMe(principal.userId()));
    }

    @GetMapping("/consents")
    public ApiResponse<List<ConsentStatusResponse>> getConsents(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(consentService.getStatus(principal.userId()));
    }

    @PostMapping("/consents")
    public ApiResponse<List<ConsentStatusResponse>> updateConsents(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ConsentUpdateRequest request) {
        return ApiResponse.ok(consentService.updateConsents(principal.userId(), request));
    }

    @GetMapping("/notification-settings")
    public ApiResponse<NotificationSettingResponse> getNotificationSettings(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(notificationSettingService.get(principal.userId()));
    }

    @PatchMapping("/notification-settings")
    public ApiResponse<NotificationSettingResponse> updateNotificationSettings(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody NotificationSettingUpdateRequest request) {
        return ApiResponse.ok(notificationSettingService.update(principal.userId(), request));
    }
}
