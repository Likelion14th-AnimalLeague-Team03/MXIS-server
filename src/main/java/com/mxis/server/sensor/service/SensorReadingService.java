package com.mxis.server.sensor.service;

import com.mxis.server.care.service.CareDiagnosisJobService;
import com.mxis.server.common.exception.BusinessException;
import com.mxis.server.common.exception.ErrorCode;
import com.mxis.server.device.entity.Device;
import com.mxis.server.notification.service.NotificationService;
import com.mxis.server.product.entity.ProductDevice;
import com.mxis.server.product.repository.ProductDeviceRepository;
import com.mxis.server.product.service.ProductDeviceMutationLock;
import com.mxis.server.sensor.dto.SensorReadingBatchRequest;
import com.mxis.server.sensor.dto.SensorReadingBatchResponse;
import com.mxis.server.sensor.dto.SensorReadingItem;
import com.mxis.server.sensor.entity.SensorReading;
import com.mxis.server.sensor.repository.SensorReadingRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SensorReadingService {
    private final SensorReadingRepository sensorReadingRepository;
    private final ProductDeviceRepository productDeviceRepository;
    private final ProductDeviceMutationLock mutationLock;
    private final CareDiagnosisJobService diagnosisJobs;
    private final NotificationService notificationService;
    private final Clock clock;

    @Transactional
    public SensorReadingBatchResponse syncBatch(Long userId, Long deviceId, SensorReadingBatchRequest request) {
        mutationLock.lockOwner(userId);
        Device device = mutationLock.lockOwnedDevice(userId, deviceId);
        LocalDateTime syncedAt = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);

        Map<Long, SensorReadingItem> unique = new LinkedHashMap<>();
        for (SensorReadingItem item : request.readings()) {
            if (item.measuredAt().isAfter(syncedAt)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "측정 시각은 서버 수신 시각보다 미래일 수 없습니다.");
            }
            SensorReadingItem previous = unique.putIfAbsent(item.sequenceNumber(), item);
            if (previous != null && !samePayload(previous, item)) {
                throw sequenceConflict();
            }
        }
        // The device row serializes concurrent uploads. A competing transaction sees committed rows
        // before this lookup, and the existing unique constraint remains the final database guard.
        Map<Long, SensorReading> existing = sensorReadingRepository
                .findExistingReadings(deviceId, List.copyOf(unique.keySet())).stream()
                .collect(Collectors.toMap(SensorReading::getSequenceNumber, Function.identity()));
        List<ProductDevice> history = productDeviceRepository.findHistoryByDeviceId(deviceId);
        List<SensorReading> toSave = new ArrayList<>();
        for (SensorReadingItem item : unique.values()) {
            SensorReading replay = existing.get(item.sequenceNumber());
            if (replay != null) {
                // A device may be registered by a new owner. Never accept another owner's history
                // as that owner's successful replay, nor disclose its original payload.
                if (!replay.getProduct().isOwnedBy(userId) || !samePayload(item, asItem(replay))) {
                    throw sequenceConflict();
                }
                continue;
            }
            ProductDevice link = measurementLink(userId, item.measuredAt(), history);
            toSave.add(new SensorReading(link.getProduct(), device, link, item.sequenceNumber(),
                    item.temperature(), item.humidity(), item.maxShockLevel(), item.motionCount(),
                    item.isOuting(), item.measuredAt(), syncedAt));
        }
        sensorReadingRepository.saveAll(toSave);
        device.markSynced(syncedAt);

        // Persist the durable job in this same transaction. The worker can only claim it after
        // commit, and AI/network failures cannot roll back these immutable sensor records.
        Map<Long, List<SensorReading>> byProduct = toSave.stream()
                .collect(Collectors.groupingBy(reading -> reading.getProduct().getId()));
        byProduct.keySet().stream().sorted().forEach(productId -> {
            diagnosisJobs.requestAfterSensorChange(productId, syncedAt);
            List<SensorReading> productReadings = byProduct.get(productId);
            notificationService.createEnvironmentAlertIfNeeded(productReadings.get(0).getProduct(), productReadings);
        });
        return new SensorReadingBatchResponse(request.readings().size(), toSave.size(),
                request.readings().size() - toSave.size(), syncedAt);
    }

    private ProductDevice measurementLink(Long userId, LocalDateTime measuredAt, List<ProductDevice> history) {
        List<ProductDevice> matches = history.stream()
                .filter(link -> !measuredAt.isBefore(link.getAttachedAt())
                        && (link.getDetachedAt() == null || measuredAt.isBefore(link.getDetachedAt())))
                .toList();
        if (matches.isEmpty()) {
            throw new BusinessException(ErrorCode.DEVICE_NOT_LINKED_TO_PRODUCT,
                    "측정 시각에 해당하는 제품 연결 이력이 없습니다.");
        }
        if (matches.size() != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "측정 시각의 제품 연결 이력이 중복되어 귀속을 결정할 수 없습니다.");
        }
        ProductDevice link = matches.get(0);
        if (!link.getProduct().isOwnedBy(userId) || link.getProduct().isDeleted()) {
            throw new BusinessException(ErrorCode.CONFLICT, "측정 당시 제품이 현재 회원의 사용 가능한 제품이 아닙니다.");
        }
        return link;
    }

    private static BusinessException sequenceConflict() {
        return new BusinessException(ErrorCode.CONFLICT, "동일한 측정 순번에 서로 다른 데이터가 전달되었습니다.");
    }

    private static SensorReadingItem asItem(SensorReading reading) {
        return new SensorReadingItem(reading.getSequenceNumber(), reading.getTemperature(), reading.getHumidity(),
                reading.getMaxShockLevel(), reading.getMotionCount(), reading.isOuting(), reading.getMeasuredAt());
    }

    private static boolean samePayload(SensorReadingItem left, SensorReadingItem right) {
        return decimalEquals(left.temperature(), right.temperature())
                && decimalEquals(left.humidity(), right.humidity())
                && decimalEquals(left.maxShockLevel(), right.maxShockLevel())
                && Objects.equals(left.motionCount(), right.motionCount())
                && left.isOuting() == right.isOuting()
                && Objects.equals(left.measuredAt(), right.measuredAt());
    }

    private static boolean decimalEquals(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }
}
