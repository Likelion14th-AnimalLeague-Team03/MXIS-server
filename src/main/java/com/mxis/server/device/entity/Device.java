package com.mxis.server.device.entity;

import com.mxis.server.common.entity.BaseTimeEntity;
import com.mxis.server.common.enums.DeviceConnectionStatus;
import com.mxis.server.user.entity.User;
import jakarta.persistence.Column;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "devices")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Device extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "serial_number", nullable = false, unique = true, length = 100)
    private String serialNumber;

    @Column(name = "device_name", length = 50)
    private String deviceName;

    @Column(name = "mac_address", length = 50)
    private String macAddress;

    @Column(name = "firmware_version", length = 20)
    private String firmwareVersion;

    @Column(name = "device_image_url", length = 500)
    private String deviceImageUrl;

    @Column(name = "battery_level")
    private Integer batteryLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_status", nullable = false, length = 20)
    private DeviceConnectionStatus connectionStatus = DeviceConnectionStatus.DISCONNECTED;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "registered_at", nullable = false)
    private LocalDateTime registeredAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public Device(User user, String serialNumber, String deviceName, String macAddress,
                  String firmwareVersion, String deviceImageUrl) {
        this.user = user;
        this.serialNumber = serialNumber;
        this.deviceName = deviceName;
        this.macAddress = macAddress;
        this.firmwareVersion = firmwareVersion;
        this.deviceImageUrl = deviceImageUrl;
        this.connectionStatus = DeviceConnectionStatus.DISCONNECTED;
        this.registeredAt = LocalDateTime.now();
    }

    public void updateStatus(DeviceConnectionStatus connectionStatus, Integer batteryLevel, LocalDateTime lastSyncedAt) {
        if (connectionStatus != null) {
            this.connectionStatus = connectionStatus;
        }
        if (batteryLevel != null) {
            this.batteryLevel = batteryLevel;
        }
        if (lastSyncedAt != null) {
            this.lastSyncedAt = lastSyncedAt;
        }
    }

    public void markSynced(LocalDateTime syncedAt) {
        this.lastSyncedAt = syncedAt;
        this.connectionStatus = DeviceConnectionStatus.CONNECTED;
    }

    public boolean isOwnedBy(Long userId) {
        return this.user.getId().equals(userId);
    }

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    /** 같은 일련번호로 재등록될 때, 삭제됐던 행을 새 소유자/정보로 되살린다. */
    public void reactivate(User user, String deviceName, String macAddress,
                            String firmwareVersion, String deviceImageUrl) {
        this.user = user;
        this.deviceName = deviceName;
        this.macAddress = macAddress;
        this.firmwareVersion = firmwareVersion;
        this.deviceImageUrl = deviceImageUrl;
        this.connectionStatus = DeviceConnectionStatus.DISCONNECTED;
        this.batteryLevel = null;
        this.lastSyncedAt = null;
        this.registeredAt = LocalDateTime.now();
        this.deletedAt = null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
