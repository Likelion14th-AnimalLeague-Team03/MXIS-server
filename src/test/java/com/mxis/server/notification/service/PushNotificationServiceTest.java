package com.mxis.server.notification.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mxis.server.common.enums.NotificationType;
import com.mxis.server.notification.client.FirebaseMessagingClient;
import com.mxis.server.user.entity.NotificationSetting;
import com.mxis.server.user.entity.User;
import com.mxis.server.user.repository.NotificationSettingRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PushNotificationServiceTest {

    private final NotificationSettingRepository repository = mock(NotificationSettingRepository.class);
    private final FirebaseMessagingClient client = mock(FirebaseMessagingClient.class);
    private final PushNotificationService service = new PushNotificationService(repository, client);

    private NotificationSetting settingFor(User user) {
        NotificationSetting setting = new NotificationSetting(user);
        ReflectionTestUtils.setField(setting, "pushPermissionGranted", true);
        ReflectionTestUtils.setField(setting, "pushToken", "token-123");
        return setting;
    }

    @Test
    void togglOff_meansNoSend() {
        User user = User.createLocal("a@mxis.com", "x", "n", "010");
        NotificationSetting setting = settingFor(user);
        ReflectionTestUtils.setField(setting, "careTimingEnabled", false);
        when(repository.findByUserId(1L)).thenReturn(Optional.of(setting));

        service.notifyUser(1L, NotificationType.CARE_TIMING, "title", "body");

        verify(client, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void toggleOnAndPermissionGranted_meansSend() {
        User user = User.createLocal("a@mxis.com", "x", "n", "010");
        NotificationSetting setting = settingFor(user);
        when(repository.findByUserId(1L)).thenReturn(Optional.of(setting));

        service.notifyUser(1L, NotificationType.CARE_TIMING, "title", "body");

        verify(client, times(1)).send("token-123", "title", "body");
    }

    @Test
    void noPushPermission_meansNoSendEvenIfToggleOn() {
        User user = User.createLocal("a@mxis.com", "x", "n", "010");
        NotificationSetting setting = new NotificationSetting(user);
        when(repository.findByUserId(1L)).thenReturn(Optional.of(setting));

        service.notifyUser(1L, NotificationType.CARE_TIMING, "title", "body");

        verify(client, never()).send(anyString(), anyString(), anyString());
    }
}
