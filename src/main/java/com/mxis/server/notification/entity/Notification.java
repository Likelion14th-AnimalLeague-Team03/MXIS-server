package com.mxis.server.notification.entity;

import com.mxis.server.common.entity.BaseTimeEntity;
import com.mxis.server.common.enums.NotificationType;
import com.mxis.server.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "device_id")
    private Long deviceId;

    @Column(name = "reservation_id")
    private Long reservationId;

    @Column(name = "care_report_id")
    private Long careReportId;

    @Column(name = "care_suggestion_id")
    private Long careSuggestionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 40)
    private NotificationType notificationType;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "deep_link", length = 500)
    private String deepLink;

    @Convert(converter = MapJsonConverter.class)
    @Column(columnDefinition = "json")
    private Map<String, Object> payload = Map.of();

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    public Notification(User user, NotificationType notificationType, String title, String message,
                        String deepLink, Map<String, Object> payload,
                        Long productId, Long deviceId, Long reservationId,
                        Long careReportId, Long careSuggestionId) {
        this.user = user;
        this.notificationType = notificationType;
        this.title = title;
        this.message = message;
        this.deepLink = deepLink;
        this.payload = payload == null ? Map.of() : Map.copyOf(payload);
        this.productId = productId;
        this.deviceId = deviceId;
        this.reservationId = reservationId;
        this.careReportId = careReportId;
        this.careSuggestionId = careSuggestionId;
    }

    public void markRead() {
        if (!read) {
            this.read = true;
            this.readAt = LocalDateTime.now();
        }
    }
}
