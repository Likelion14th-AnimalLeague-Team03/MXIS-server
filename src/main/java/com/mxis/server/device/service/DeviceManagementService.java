package com.mxis.server.device.service;

import com.mxis.server.care.dto.ScreenProductSummary;
import com.mxis.server.common.exception.BusinessException;
import com.mxis.server.common.exception.ErrorCode;
import com.mxis.server.device.dto.management.DeviceManagementSummaryResponse;
import com.mxis.server.device.entity.Device;
import com.mxis.server.product.entity.Product;
import com.mxis.server.product.entity.ProductDevice;
import com.mxis.server.product.repository.ProductDeviceRepository;
import com.mxis.server.product.repository.ProductRepository;
import com.mxis.server.sensor.entity.SensorReading;
import com.mxis.server.sensor.repository.SensorReadingRepository;
import com.mxis.server.user.entity.User;
import com.mxis.server.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeviceManagementService {

    private final ProductRepository productRepository;
    private final ProductDeviceRepository productDeviceRepository;
    private final SensorReadingRepository sensorReadingRepository;
    private final UserRepository userRepository;

    public DeviceManagementSummaryResponse getSummary(Long userId) {
        User user = userRepository.findActiveById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        List<Product> products = productRepository.findAllActiveByUserId(userId);
        Product primaryProduct = user.getPrimaryProduct();

        if (primaryProduct == null || primaryProduct.isDeleted()) {
            return new DeviceManagementSummaryResponse(
                    productImages(products), null, 0, null, null);
        }

        ProductDevice primaryLink = productDeviceRepository.findActivePrimaryByProductId(primaryProduct.getId())
                .orElse(null);
        SensorReading latestReading = sensorReadingRepository
                .findFirstByProductIdOrderByMeasuredAtDesc(primaryProduct.getId())
                .orElse(null);

        return new DeviceManagementSummaryResponse(
                productImages(products),
                ScreenProductSummary.from(primaryProduct),
                sensorReadingRepository.countTotalOutingSessions(primaryProduct.getId()),
                primaryLink == null ? null : primaryDevice(primaryLink.getDevice()),
                latestReading == null ? null : currentEnvironment(latestReading));
    }

    private List<DeviceManagementSummaryResponse.ProductImage> productImages(List<Product> products) {
        return products.stream()
                .map(product -> new DeviceManagementSummaryResponse.ProductImage(
                        product.getId(), product.getProductImageUrl()))
                .toList();
    }

    private DeviceManagementSummaryResponse.PrimaryDevice primaryDevice(Device device) {
        return new DeviceManagementSummaryResponse.PrimaryDevice(
                device.getId(),
                device.getSerialNumber(),
                device.getDeviceImageUrl(),
                device.getConnectionStatus(),
                device.getBatteryLevel(),
                device.getLastSyncedAt());
    }

    private DeviceManagementSummaryResponse.CurrentEnvironment currentEnvironment(SensorReading reading) {
        return new DeviceManagementSummaryResponse.CurrentEnvironment(
                reading.getTemperature(),
                reading.getHumidity(),
                reading.getMeasuredAt());
    }
}
