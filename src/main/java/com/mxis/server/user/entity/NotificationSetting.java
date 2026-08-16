package com.mxis.server.user.entity;

import com.mxis.server.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "notification_settings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSetting extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "care_timing_enabled", nullable = false)
    private boolean careTimingEnabled = true;

    @Column(name = "reservation_enabled", nullable = false)
    private boolean reservationEnabled = true;

    @Column(name = "device_status_enabled", nullable = false)
    private boolean deviceStatusEnabled = true;

    @Column(name = "marketing_enabled", nullable = false)
    private boolean marketingEnabled = false;

    @Column(name = "environment_alert_enabled", nullable = false)
    private boolean environmentAlertEnabled = true;

    @Column(name = "push_permission_granted", nullable = false)
    private boolean pushPermissionGranted = false;

    @Column(name = "push_token", length = 255)
    private String pushToken;

    public NotificationSetting(User user) {
        this.user = user;
    }

    public void update(Boolean careTimingEnabled, Boolean reservationEnabled, Boolean deviceStatusEnabled,
                        Boolean marketingEnabled, Boolean environmentAlertEnabled,
                        Boolean pushPermissionGranted, String pushToken) {
        if (careTimingEnabled != null) this.careTimingEnabled = careTimingEnabled;
        if (reservationEnabled != null) this.reservationEnabled = reservationEnabled;
        if (deviceStatusEnabled != null) this.deviceStatusEnabled = deviceStatusEnabled;
        if (marketingEnabled != null) this.marketingEnabled = marketingEnabled;
        if (environmentAlertEnabled != null) this.environmentAlertEnabled = environmentAlertEnabled;
        if (pushPermissionGranted != null) this.pushPermissionGranted = pushPermissionGranted;
        if (pushToken != null) this.pushToken = pushToken;
    }
}
