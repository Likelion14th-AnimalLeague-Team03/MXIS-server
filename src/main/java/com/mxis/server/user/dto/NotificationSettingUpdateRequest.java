package com.mxis.server.user.dto;

public record NotificationSettingUpdateRequest(
        Boolean careTimingEnabled,
        Boolean reservationEnabled,
        Boolean deviceStatusEnabled,
        Boolean marketingEnabled,
        Boolean pushPermissionGranted,
        String pushToken
) {
}
