package com.mxis.server.user.dto;

import com.mxis.server.user.entity.NotificationSetting;

public record NotificationSettingResponse(
        boolean careTimingEnabled,
        boolean reservationEnabled,
        boolean deviceStatusEnabled,
        boolean marketingEnabled,
        boolean environmentAlertEnabled,
        boolean pushPermissionGranted
) {
    public static NotificationSettingResponse from(NotificationSetting setting) {
        return new NotificationSettingResponse(
                setting.isCareTimingEnabled(),
                setting.isReservationEnabled(),
                setting.isDeviceStatusEnabled(),
                setting.isMarketingEnabled(),
                setting.isEnvironmentAlertEnabled(),
                setting.isPushPermissionGranted());
    }
}
