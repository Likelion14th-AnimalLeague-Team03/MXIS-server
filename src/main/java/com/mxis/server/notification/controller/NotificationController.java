package com.mxis.server.notification.controller;

import com.mxis.server.common.enums.NotificationType;
import com.mxis.server.common.response.ApiResponse;
import com.mxis.server.common.security.UserPrincipal;
import com.mxis.server.notification.dto.NotificationPageResponse;
import com.mxis.server.notification.dto.NotificationReadResult;
import com.mxis.server.notification.dto.NotificationResponse;
import com.mxis.server.notification.dto.NotificationUnreadCountResponse;
import com.mxis.server.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<NotificationPageResponse> getNotifications(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) NotificationType type,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(notificationService.getNotifications(
                principal.userId(), type, unreadOnly, page, size));
    }

    @GetMapping("/unread-count")
    public ApiResponse<NotificationUnreadCountResponse> getUnreadCount(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(notificationService.unreadCount(principal.userId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<NotificationResponse> getNotification(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ApiResponse.ok(notificationService.getNotification(principal.userId(), id));
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<NotificationReadResult> markRead(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ApiResponse.ok(notificationService.markRead(principal.userId(), id));
    }

    @PatchMapping("/read-all")
    public ApiResponse<NotificationUnreadCountResponse> markAllRead(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(notificationService.markAllRead(principal.userId()));
    }
}
