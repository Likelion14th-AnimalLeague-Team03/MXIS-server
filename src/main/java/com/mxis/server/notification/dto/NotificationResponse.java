package com.mxis.server.notification.dto;

import com.mxis.server.common.enums.NotificationType;
import com.mxis.server.notification.entity.Notification;
import java.time.LocalDateTime;
import java.util.Map;

public record NotificationResponse(
        Long id,
        NotificationType type,
        String title,
        String message,
        String deepLink,
        Map<String, Object> payload,
        RelatedIds relatedIds,
        boolean isRead,
        LocalDateTime readAt,
        LocalDateTime sentAt,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getNotificationType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getDeepLink(),
                notification.getPayload(),
                new RelatedIds(
                        notification.getProductId(),
                        notification.getDeviceId(),
                        notification.getReservationId(),
                        notification.getCareReportId(),
                        notification.getCareSuggestionId()),
                notification.isRead(),
                notification.getReadAt(),
                notification.getSentAt(),
                notification.getCreatedAt());
    }

    public record RelatedIds(
            Long productId,
            Long deviceId,
            Long reservationId,
            Long careReportId,
            Long careSuggestionId
    ) {
    }
}
