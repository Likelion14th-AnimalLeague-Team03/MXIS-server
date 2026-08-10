package com.mxis.server.product.entity;

import com.mxis.server.common.entity.BaseTimeEntity;
import com.mxis.server.common.enums.ProductDeviceRole;
import com.mxis.server.device.entity.Device;
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

/**
 * Product : Device N:M 연결 이력.
 * active_primary_product_id (Virtual Generated Column, DB에서만 존재)로
 * "제품당 활성 PRIMARY_SENSOR 최대 1개" 제약을 강제한다 - 엔티티에는 매핑하지 않는다.
 */
@Getter
@Entity
@Table(name = "product_devices")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductDevice extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductDeviceRole role = ProductDeviceRole.SECONDARY;

    @Column(name = "attached_at", nullable = false)
    private LocalDateTime attachedAt;

    @Column(name = "detached_at")
    private LocalDateTime detachedAt;

    public ProductDevice(Product product, Device device, ProductDeviceRole role) {
        this.product = product;
        this.device = device;
        this.role = role == null ? ProductDeviceRole.SECONDARY : role;
        this.attachedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return detachedAt == null;
    }

    public boolean isPrimary() {
        return role == ProductDeviceRole.PRIMARY_SENSOR;
    }

    public void promoteToPrimary() {
        this.role = ProductDeviceRole.PRIMARY_SENSOR;
    }

    public void demoteToSecondary() {
        this.role = ProductDeviceRole.SECONDARY;
    }

    public void detach() {
        this.detachedAt = LocalDateTime.now();
    }
}
