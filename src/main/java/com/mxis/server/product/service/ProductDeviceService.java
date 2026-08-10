package com.mxis.server.product.service;

import com.mxis.server.common.enums.ProductDeviceRole;
import com.mxis.server.common.exception.BusinessException;
import com.mxis.server.common.exception.ErrorCode;
import com.mxis.server.device.entity.Device;
import com.mxis.server.device.repository.DeviceRepository;
import com.mxis.server.product.dto.ProductDeviceLinkRequest;
import com.mxis.server.product.dto.ProductDeviceResponse;
import com.mxis.server.product.entity.Product;
import com.mxis.server.product.entity.ProductDevice;
import com.mxis.server.product.repository.ProductDeviceRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductDeviceService {

    private final ProductDeviceRepository productDeviceRepository;
    private final DeviceRepository deviceRepository;
    private final ProductService productService;
    private final EntityManager entityManager;

    @Transactional
    public ProductDeviceResponse link(Long userId, Long productId, ProductDeviceLinkRequest request) {
        Product product = productService.getOwnedProduct(userId, productId);
        Device device = getOwnedDevice(userId, request.deviceId());

        if (productDeviceRepository.findActiveByProductIdAndDeviceId(productId, device.getId()).isPresent()) {
            throw new BusinessException(ErrorCode.DEVICE_ALREADY_LINKED);
        }
        // 실물 참(Charm)은 한 번에 하나의 가방에만 부착 가능하다는 전제 - 이미 다른 제품에
        // 활성 연결돼 있는 기기는 재연결 전에 먼저 해제되어야 한다.
        if (!productDeviceRepository.findActiveByDeviceId(device.getId()).isEmpty()) {
            throw new BusinessException(ErrorCode.DEVICE_ALREADY_LINKED, "이미 다른 제품에 연결된 기기입니다.");
        }

        ProductDeviceRole requestedRole = request.role() == null ? ProductDeviceRole.SECONDARY : request.role();
        if (requestedRole == ProductDeviceRole.PRIMARY_SENSOR
                && productDeviceRepository.findActivePrimaryByProductId(productId).isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "이미 대표 센서가 지정되어 있습니다. 변경하려면 대표 센서 변경 API를 사용하세요.");
        }

        ProductDevice link = new ProductDevice(product, device, requestedRole);
        return ProductDeviceResponse.from(productDeviceRepository.save(link));
    }

    public List<ProductDeviceResponse> getLinkedDevices(Long userId, Long productId) {
        productService.getOwnedProduct(userId, productId);
        return productDeviceRepository.findActiveByProductId(productId).stream()
                .map(ProductDeviceResponse::from)
                .toList();
    }

    @Transactional
    public ProductDeviceResponse promoteToPrimary(Long userId, Long productId, Long deviceId) {
        productService.getOwnedProduct(userId, productId);

        ProductDevice target = productDeviceRepository.findActiveByProductIdAndDeviceId(productId, deviceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_DEVICE_LINK_NOT_FOUND));

        if (target.isPrimary()) {
            return ProductDeviceResponse.from(target);
        }

        // MariaDB unique index(active_primary_product_id)는 Postgres의 지연(deferrable) 제약이 아니라
        // 문장 단위로 즉시 검사되므로, 반드시 "기존 대표 센서를 먼저 해제 → flush" 한 뒤에
        // 새 기기를 대표 센서로 승격해야 유니크 제약 위반을 피할 수 있다.
        productDeviceRepository.findActivePrimaryByProductId(productId)
                .filter(current -> !current.getId().equals(target.getId()))
                .ifPresent(current -> {
                    current.demoteToSecondary();
                    entityManager.flush();
                });

        target.promoteToPrimary();
        entityManager.flush();

        return ProductDeviceResponse.from(target);
    }

    @Transactional
    public void unlink(Long userId, Long productId, Long deviceId) {
        productService.getOwnedProduct(userId, productId);

        ProductDevice link = productDeviceRepository.findActiveByProductIdAndDeviceId(productId, deviceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_DEVICE_LINK_NOT_FOUND));

        link.detach();
    }

    private Device getOwnedDevice(Long userId, Long deviceId) {
        Device device = deviceRepository.findActiveById(deviceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_FOUND));
        if (!device.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.DEVICE_NOT_OWNED);
        }
        return device;
    }
}
