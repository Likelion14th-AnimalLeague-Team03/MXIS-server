package com.mxis.server.notification.service;

import com.mxis.server.common.enums.NotificationType;
import com.mxis.server.notification.client.FirebaseMessagingClient;
import com.mxis.server.user.entity.NotificationSetting;
import com.mxis.server.user.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 알림 설정 토글을 확인한 뒤에만 실제로 푸시를 보낸다. 트리거 지점(care/reservation/device/sensor)에서 호출. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PushNotificationService {

    private final NotificationSettingRepository notificationSettingRepository;
    private final FirebaseMessagingClient client;

    public void notifyUser(Long userId, NotificationType type, String title, String body) {
        notificationSettingRepository.findByUserId(userId).ifPresent(setting -> notify(setting, type, title, body));
    }

    private void notify(NotificationSetting setting, NotificationType type, String title, String body) {
        if (!isEnabled(setting, type) || !setting.isPushPermissionGranted() || setting.getPushToken() == null) {
            return;
        }
        client.send(setting.getPushToken(), title, body);
    }

    private static boolean isEnabled(NotificationSetting setting, NotificationType type) {
        return switch (type) {
            case CARE_TIMING -> setting.isCareTimingEnabled();
            case RESERVATION -> setting.isReservationEnabled();
            case DEVICE_STATUS -> setting.isDeviceStatusEnabled();
            case ENVIRONMENT_ALERT -> setting.isEnvironmentAlertEnabled();
        };
    }
}
