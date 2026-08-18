package com.mxis.server.device.service;

import com.mxis.server.care.dto.ScreenProductSummary;
import com.mxis.server.common.exception.BusinessException;
import com.mxis.server.common.exception.ErrorCode;
import com.mxis.server.device.dto.management.DeviceManagementSummaryResponse;
import com.mxis.server.device.dto.management.ProductDeviceManagementSummaryResponse;
import com.mxis.server.device.entity.Device;
import com.mxis.server.product.entity.Product;
import com.mxis.server.product.entity.ProductDevice;
import com.mxis.server.product.repository.ProductDeviceRepository;
import com.mxis.server.product.repository.ProductRepository;
import com.mxis.server.sensor.entity.SensorReading;
import com.mxis.server.sensor.repository.SensorReadingRepository;
import com.mxis.server.user.entity.User;
import com.mxis.server.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeviceManagementService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

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

    public ProductDeviceManagementSummaryResponse getProductSummary(Long userId, Long productId) {
        User user = userRepository.findActiveById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Product product = productRepository.findActiveById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        if (!product.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_OWNED);
        }

        List<ProductDevice> activeLinks = productDeviceRepository.findActiveByProductId(productId);
        SensorReading latestReading = sensorReadingRepository
                .findFirstByProductIdOrderByMeasuredAtDesc(productId)
                .orElse(null);
        List<ProductDeviceManagementSummaryResponse.ConnectedDevice> connectedDevices = activeLinks.stream()
                .map(ProductDeviceManagementSummaryResponse.ConnectedDevice::from)
                .toList();
        ProductDeviceManagementSummaryResponse.ConnectedDevice primaryDevice = activeLinks.stream()
                .filter(ProductDevice::isPrimary)
                .findFirst()
                .map(ProductDeviceManagementSummaryResponse.ConnectedDevice::from)
                .orElse(null);

        boolean isPrimary = user.getPrimaryProduct() != null
                && user.getPrimaryProduct().getId().equals(product.getId());

        return new ProductDeviceManagementSummaryResponse(
                ProductDeviceManagementSummaryResponse.ProductSummary.from(product, isPrimary),
                latestReading == null ? null : ProductDeviceManagementSummaryResponse.CurrentEnvironment.from(
                        latestReading, formatMeasuredAt(latestReading.getMeasuredAt())),
                sensorReadingRepository.countTotalOutingSessions(productId),
                primaryDevice,
                connectedDevices);
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
                formatMeasuredAt(reading.getMeasuredAt()));
    }

    private String formatMeasuredAt(LocalDateTime measuredAt) {
        if (measuredAt == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        long minutes = ChronoUnit.MINUTES.between(measuredAt, now);
        if (minutes < 1) {
            return "방금전";
        }
        if (minutes < 60) {
            return minutes + "분 전";
        }

        long hours = ChronoUnit.HOURS.between(measuredAt, now);
        if (hours < 24) {
            return hours + "시간 전";
        }

        long days = ChronoUnit.DAYS.between(measuredAt.toLocalDate(), now.toLocalDate());
        if (days < 10) {
            return days + "일 전";
        }
        return measuredAt.toLocalDate().format(DATE_FORMATTER);
    }
}
