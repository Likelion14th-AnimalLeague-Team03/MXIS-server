package com.mxis.server.notification.service;

import com.mxis.server.common.enums.DeviceConnectionStatus;
import com.mxis.server.common.enums.NotificationType;
import com.mxis.server.common.exception.BusinessException;
import com.mxis.server.common.exception.ErrorCode;
import com.mxis.server.device.entity.Device;
import com.mxis.server.notification.dto.NotificationPageResponse;
import com.mxis.server.notification.dto.NotificationReadResult;
import com.mxis.server.notification.dto.NotificationResponse;
import com.mxis.server.notification.dto.NotificationUnreadCountResponse;
import com.mxis.server.notification.entity.Notification;
import com.mxis.server.notification.repository.NotificationRepository;
import com.mxis.server.product.entity.Product;
import com.mxis.server.care.entity.CareSuggestion;
import com.mxis.server.reservation.entity.Reservation;
import com.mxis.server.sensor.entity.SensorReading;
import com.mxis.server.user.entity.NotificationSetting;
import com.mxis.server.user.entity.User;
import com.mxis.server.user.repository.NotificationSettingRepository;
import java.util.HashMap;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int LOW_BATTERY_THRESHOLD = 20;
    private static final int DEVICE_STATUS_DEDUP_HOURS = 12;
    private static final int ENVIRONMENT_DEDUP_HOURS = 24;
    private static final BigDecimal HIGH_HUMIDITY_THRESHOLD = BigDecimal.valueOf(65);
    private static final BigDecimal HIGH_TEMPERATURE_THRESHOLD = BigDecimal.valueOf(30);
    private static final BigDecimal DRY_HUMIDITY_THRESHOLD = BigDecimal.valueOf(30);
    private static final BigDecimal SHOCK_THRESHOLD = BigDecimal.valueOf(1.5);

    private final NotificationRepository notificationRepository;
    private final NotificationSettingRepository notificationSettingRepository;

    public NotificationPageResponse getNotifications(
            Long userId, NotificationType type, boolean unreadOnly, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<Notification> result = notificationRepository.findByUser(
                userId, type, unreadOnly, PageRequest.of(safePage, safeSize));

        return new NotificationPageResponse(
                result.getContent().stream().map(NotificationResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext(),
                notificationRepository.countByUserIdAndReadFalse(userId));
    }

    public NotificationResponse getNotification(Long userId, Long notificationId) {
        return NotificationResponse.from(getOwnedNotification(userId, notificationId));
    }

    @Transactional
    public NotificationReadResult markRead(Long userId, Long notificationId) {
        Notification notification = getOwnedNotification(userId, notificationId);
        notification.markRead();
        return new NotificationReadResult(notification.getId(), notification.isRead(), notification.getReadAt());
    }

    @Transactional
    public NotificationUnreadCountResponse markAllRead(Long userId) {
        notificationRepository.markAllRead(userId, LocalDateTime.now());
        return unreadCount(userId);
    }

    public NotificationUnreadCountResponse unreadCount(Long userId) {
        return new NotificationUnreadCountResponse(notificationRepository.countByUserIdAndReadFalse(userId));
    }

    @Transactional
    public void createDeviceStatusNotificationIfNeeded(Device device) {
        NotificationSetting setting = setting(device.getUser().getId());
        if (!setting.isDeviceStatusEnabled()) {
            return;
        }
        if (!shouldNotifyDeviceStatus(device)) {
            return;
        }
        if (notificationRepository.existsByUserIdAndNotificationTypeAndDeviceIdAndCreatedAtAfter(
                device.getUser().getId(),
                NotificationType.DEVICE_STATUS,
                device.getId(),
                LocalDateTime.now().minusHours(DEVICE_STATUS_DEDUP_HOURS))) {
            return;
        }

        String title = device.getBatteryLevel() != null && device.getBatteryLevel() <= LOW_BATTERY_THRESHOLD
                ? "MXIS 배터리를 확인해주세요"
                : "MXIS 연결 상태를 확인해주세요";
        String message = device.getBatteryLevel() != null && device.getBatteryLevel() <= LOW_BATTERY_THRESHOLD
                ? "배터리가 낮아 센서 데이터 수집이 중단될 수 있어요."
                : "기기 연결이 불안정해 최신 케어 분석이 지연될 수 있어요.";

        create(
                device.getUser(),
                NotificationType.DEVICE_STATUS,
                title,
                message,
                "/devices/%d".formatted(device.getId()),
                deviceStatusPayload(device),
                null,
                device.getId(),
                null,
                null,
                null);
    }

    @Transactional
    public void createEnvironmentAlertIfNeeded(Product product, List<SensorReading> savedReadings) {
        if (savedReadings == null || savedReadings.isEmpty()) {
            return;
        }
        NotificationSetting setting = setting(product.getUser().getId());
        if (!setting.isEnvironmentAlertEnabled()) {
            return;
        }
        EnvironmentFactor factor = detectEnvironmentFactor(savedReadings);
        if (factor == null) {
            return;
        }
        if (notificationRepository.existsByUserIdAndNotificationTypeAndProductIdAndCreatedAtAfter(
                product.getUser().getId(),
                NotificationType.ENVIRONMENT_ALERT,
                product.getId(),
                LocalDateTime.now().minusHours(ENVIRONMENT_DEDUP_HOURS))) {
            return;
        }

        create(
                product.getUser(),
                NotificationType.ENVIRONMENT_ALERT,
                "보관 환경을 확인해주세요",
                factor.message(),
                "/care/products/%d/environment".formatted(product.getId()),
                Map.of("productId", product.getId(), "factor", factor.code()),
                product.getId(),
                null,
                null,
                null,
                null);
    }

    @Transactional
    public void createCareTimingNotificationIfNeeded(CareSuggestion suggestion) {
        User user = suggestion.getProduct().getUser();
        NotificationSetting setting = setting(user.getId());
        if (!setting.isCareTimingEnabled()) {
            return;
        }
        if (notificationRepository.existsByUserIdAndNotificationTypeAndCareSuggestionId(
                user.getId(), NotificationType.CARE_TIMING, suggestion.getId())) {
            return;
        }

        create(
                user,
                NotificationType.CARE_TIMING,
                "케어가 필요한 시점이에요",
                suggestion.getMessage(),
                "/care/products/%d/summary".formatted(suggestion.getProduct().getId()),
                careTimingPayload(suggestion),
                suggestion.getProduct().getId(),
                null,
                null,
                suggestion.getCareReport().getId(),
                suggestion.getId());
    }

    @Transactional
    public void createReservationReminderIfNeeded(Reservation reservation) {
        User user = reservation.getUser();
        NotificationSetting setting = setting(user.getId());
        if (!setting.isReservationEnabled()) {
            return;
        }
        if (notificationRepository.existsByUserIdAndNotificationTypeAndReservationId(
                user.getId(), NotificationType.RESERVATION_REMINDER, reservation.getId())) {
            return;
        }

        String reservedAt = "%s %s".formatted(reservation.getReservedDate(), reservation.getReservedTime());
        create(
                user,
                NotificationType.RESERVATION_REMINDER,
                "예약 시간이 다가오고 있어요",
                "%s 예약이 %s에 예정되어 있어요.".formatted(reservation.getStore().getStoreName(), reservedAt),
                "/reservations/%d".formatted(reservation.getId()),
                Map.of(
                        "reservationId", reservation.getId(),
                        "productId", reservation.getProduct().getId(),
                        "storeId", reservation.getStore().getId(),
                        "reservedDate", reservation.getReservedDate().toString(),
                        "reservedTime", reservation.getReservedTime().toString()),
                reservation.getProduct().getId(),
                null,
                reservation.getId(),
                null,
                reservation.getCareSuggestion() == null ? null : reservation.getCareSuggestion().getId());
    }

    @Transactional
    public Notification create(User user, NotificationType type, String title, String message,
                               String deepLink, Map<String, Object> payload,
                               Long productId, Long deviceId, Long reservationId,
                               Long careReportId, Long careSuggestionId) {
        return notificationRepository.save(new Notification(
                user, type, title, message, deepLink, payload,
                productId, deviceId, reservationId, careReportId, careSuggestionId));
    }

    private Notification getOwnedNotification(Long userId, Long notificationId) {
        return notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));
    }

    private NotificationSetting setting(Long userId) {
        return notificationSettingRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "알림 설정 정보를 찾을 수 없습니다."));
    }

    private boolean shouldNotifyDeviceStatus(Device device) {
        boolean lowBattery = device.getBatteryLevel() != null && device.getBatteryLevel() <= LOW_BATTERY_THRESHOLD;
        boolean disconnected = device.getConnectionStatus() == DeviceConnectionStatus.DISCONNECTED
                || device.getConnectionStatus() == DeviceConnectionStatus.ERROR;
        return lowBattery || disconnected;
    }

    private EnvironmentFactor detectEnvironmentFactor(List<SensorReading> readings) {
        for (SensorReading reading : readings) {
            if (reading.getHumidity() != null && reading.getHumidity().compareTo(HIGH_HUMIDITY_THRESHOLD) >= 0) {
                return new EnvironmentFactor("humidity", "최근 습도가 안정 범위를 벗어난 기록이 있어요.");
            }
            if (reading.getTemperature() != null && reading.getTemperature().compareTo(HIGH_TEMPERATURE_THRESHOLD) >= 0) {
                return new EnvironmentFactor("temperature_heat", "최근 온도가 높게 감지되어 보관 위치를 확인하는 것이 좋아요.");
            }
            if (reading.getHumidity() != null && reading.getHumidity().compareTo(DRY_HUMIDITY_THRESHOLD) < 0) {
                return new EnvironmentFactor("dryness", "건조한 환경 노출이 감지되어 과도한 제습을 피하는 것이 좋아요.");
            }
            if (reading.getMaxShockLevel() != null && reading.getMaxShockLevel().compareTo(SHOCK_THRESHOLD) >= 0) {
                return new EnvironmentFactor("handling", "움직임 또는 충격 노출이 감지되어 보관 위치를 확인해주세요.");
            }
        }
        return null;
    }

    private Map<String, Object> deviceStatusPayload(Device device) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("deviceId", device.getId());
        payload.put("connectionStatus", device.getConnectionStatus().name());
        if (device.getBatteryLevel() != null) {
            payload.put("batteryLevel", device.getBatteryLevel());
        }
        return payload;
    }

    private Map<String, Object> careTimingPayload(CareSuggestion suggestion) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("productId", suggestion.getProduct().getId());
        payload.put("careSuggestionId", suggestion.getId());
        if (suggestion.getRecommendedService() != null) {
            payload.put("recommendedService", suggestion.getRecommendedService());
        }
        return payload;
    }

    private record EnvironmentFactor(String code, String message) {
    }
}
