package com.mxis.server.device.dto.management;

import com.mxis.server.common.enums.DeviceConnectionStatus;
import com.mxis.server.common.enums.ProductDeviceRole;
import com.mxis.server.device.entity.Device;
import com.mxis.server.product.entity.Product;
import com.mxis.server.product.entity.ProductDevice;
import com.mxis.server.sensor.entity.SensorReading;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ProductDeviceManagementSummaryResponse(
        ProductSummary product,
        CurrentEnvironment currentEnvironment,
        long totalOutingCount,
        ConnectedDevice primaryDevice,
        List<ConnectedDevice> connectedDevices
) {
    public record ProductSummary(
            Long productId,
            String productImageUrl,
            String productName,
            String materialId,
            String materialDisplayName,
            String color,
            String modelCode,
            String dppCode,
            boolean isPrimary
    ) {
        public static ProductSummary from(Product product, boolean isPrimary) {
            return new ProductSummary(
                    product.getId(),
                    product.getProductImageUrl(),
                    product.getProductName(),
                    product.getMaterialId(),
                    product.getMaterialDisplayName(),
                    product.getColor(),
                    product.getModelCode(),
                    product.getDppCode(),
                    isPrimary);
        }
    }

    public record CurrentEnvironment(
            BigDecimal temperature,
            BigDecimal humidity,
            LocalDateTime measuredAt
    ) {
        public static CurrentEnvironment from(SensorReading reading) {
            return new CurrentEnvironment(
                    reading.getTemperature(),
                    reading.getHumidity(),
                    reading.getMeasuredAt());
        }
    }

    public record ConnectedDevice(
            Long deviceId,
            String serialNumber,
            String deviceName,
            String deviceImageUrl,
            ProductDeviceRole role,
            DeviceConnectionStatus connectionStatus,
            Integer batteryLevel,
            LocalDateTime lastSyncedAt
    ) {
        public static ConnectedDevice from(ProductDevice productDevice) {
            Device device = productDevice.getDevice();
            return new ConnectedDevice(
                    device.getId(),
                    device.getSerialNumber(),
                    device.getDeviceName(),
                    device.getDeviceImageUrl(),
                    productDevice.getRole(),
                    device.getConnectionStatus(),
                    device.getBatteryLevel(),
                    device.getLastSyncedAt());
        }
    }
}
