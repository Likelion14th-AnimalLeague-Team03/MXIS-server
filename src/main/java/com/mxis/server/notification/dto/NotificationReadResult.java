package com.mxis.server.notification.dto;

import java.time.LocalDateTime;

public record NotificationReadResult(
        Long id,
        boolean isRead,
        LocalDateTime readAt
) {
}
