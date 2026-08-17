package com.mxis.server.user.dto;

public record NotificationSettingUpdateRequest(
        Boolean careTimingEnabled,
        Boolean reservationEnabled,
        Boolean deviceStatusEnabled,
        Boolean marketingEnabled,
        Boolean environmentAlertEnabled,
        Boolean pushPermissionGranted,
        String pushToken
) {
}
