package com.mxis.server.sensor.entity;

import com.mxis.server.device.entity.Device;
import com.mxis.server.product.entity.Product;
import com.mxis.server.product.entity.ProductDevice;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 센서 원시 측정 기록. Immutable Entity - INSERT 이후 수정하지 않는다.
 * ERD 상 다른 Immutable Entity(consents, care_reports)와 달리 이 테이블은 created_at이 아니라
 * measured_at(실측 시각) / synced_at(서버 수신 시각) 두 개의 시간 컬럼을 가지므로
 * common/entity의 BaseCreatedAtEntity를 상속하지 않고 필드를 직접 선언한다.
 */
@Getter
@Entity
@Table(name = "sensor_readings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SensorReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_device_id", nullable = false)
    private ProductDevice productDevice;

    @Column(name = "sequence_number", nullable = false)
    private Long sequenceNumber;

    @Column(precision = 5, scale = 2)
    private BigDecimal temperature;

    @Column(precision = 5, scale = 2)
    private BigDecimal humidity;

    @Column(name = "max_shock_level", precision = 6, scale = 3)
    private BigDecimal maxShockLevel;

    @Column(name = "motion_count")
    private Integer motionCount;

    @Column(name = "is_outing", nullable = false)
    private boolean isOuting;

    @Column(name = "measured_at", nullable = false)
    private LocalDateTime measuredAt;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    public SensorReading(Product product, Device device, ProductDevice productDevice, Long sequenceNumber,
                          BigDecimal temperature, BigDecimal humidity, BigDecimal maxShockLevel,
                          Integer motionCount, boolean isOuting, LocalDateTime measuredAt) {
        this.product = product;
        this.device = device;
        this.productDevice = productDevice;
        this.sequenceNumber = sequenceNumber;
        this.temperature = temperature;
        this.humidity = humidity;
        this.maxShockLevel = maxShockLevel;
        this.motionCount = motionCount;
        this.isOuting = isOuting;
        this.measuredAt = measuredAt;
        this.syncedAt = LocalDateTime.now();
    }
}
