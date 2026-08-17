package com.mxis.server.sensor.service;

import com.mxis.server.care.service.CareDiagnosisService;
import com.mxis.server.common.exception.BusinessException;
import com.mxis.server.common.exception.ErrorCode;
import com.mxis.server.device.entity.Device;
import com.mxis.server.device.repository.DeviceRepository;
import com.mxis.server.notification.service.NotificationService;
import com.mxis.server.product.entity.ProductDevice;
import com.mxis.server.product.repository.ProductDeviceRepository;
import com.mxis.server.sensor.dto.SensorReadingBatchRequest;
import com.mxis.server.sensor.dto.SensorReadingBatchResponse;
import com.mxis.server.sensor.dto.SensorReadingItem;
import com.mxis.server.sensor.entity.SensorReading;
import com.mxis.server.sensor.repository.SensorReadingRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SensorReadingService {

    private final SensorReadingRepository sensorReadingRepository;
    private final DeviceRepository deviceRepository;
    private final ProductDeviceRepository productDeviceRepository;
    private final CareDiagnosisService careDiagnosisService;
    private final NotificationService notificationService;

    @Transactional
    public SensorReadingBatchResponse syncBatch(Long userId, Long deviceId, SensorReadingBatchRequest request) {
        Device device = deviceRepository.findActiveById(deviceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_FOUND));
        if (!device.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.DEVICE_NOT_OWNED);
        }

        // 기기는 최대 하나의 제품에만 활성 연결될 수 있다 (ProductDeviceService.link에서 강제).
        List<ProductDevice> activeLinks = productDeviceRepository.findActiveByDeviceId(deviceId);
        if (activeLinks.isEmpty()) {
            throw new BusinessException(ErrorCode.DEVICE_NOT_LINKED_TO_PRODUCT);
        }
        ProductDevice link = activeLinks.get(0);

        List<Long> requestedSequenceNumbers = request.readings().stream()
                .map(SensorReadingItem::sequenceNumber)
                .toList();

        // BLE 재전송 등으로 인한 중복 sequence_number는 (device_id, sequence_number) 유니크 제약 대상이므로
        // 저장 전에 걸러내어 부분 실패 없이 멱등하게 처리한다.
        Set<Long> existing = Set.copyOf(
                sensorReadingRepository.findExistingSequenceNumbers(deviceId, requestedSequenceNumbers));

        List<SensorReading> toSave = request.readings().stream()
                .filter(item -> !existing.contains(item.sequenceNumber()))
                .map(item -> new SensorReading(
                        link.getProduct(),
                        device,
                        link,
                        item.sequenceNumber(),
                        item.temperature(),
                        item.humidity(),
                        item.maxShockLevel(),
                        item.motionCount(),
                        item.isOuting(),
                        item.measuredAt()))
                .toList();

        sensorReadingRepository.saveAll(toSave);

        LocalDateTime syncedAt = LocalDateTime.now();
        device.markSynced(syncedAt);

        // 동기화 직후 진단을 갱신한다. 집계 쿼리가 방금 저장한 행을 보도록 먼저 flush한다
        // (네이티브 집계 쿼리는 JPA의 자동 flush 대상이 아니다).
        // ponytail: 같은 트랜잭션에서 동기 처리. 배치가 커져 응답이 느려지면 @Async로 분리한다.
        sensorReadingRepository.flush();
        careDiagnosisService.regenerate(link.getProduct());
        notificationService.createEnvironmentAlertIfNeeded(link.getProduct(), toSave);

        return new SensorReadingBatchResponse(
                request.readings().size(),
                toSave.size(),
                existing.size(),
                syncedAt);
    }
}
