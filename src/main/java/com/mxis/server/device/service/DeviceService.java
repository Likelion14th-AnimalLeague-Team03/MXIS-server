package com.mxis.server.device.service;

import com.mxis.server.common.enums.DeviceConnectionStatus;
import com.mxis.server.common.enums.NotificationType;
import com.mxis.server.common.exception.BusinessException;
import com.mxis.server.common.exception.ErrorCode;
import com.mxis.server.device.dto.DeviceLookupResponse;
import com.mxis.server.device.dto.DeviceRegisterRequest;
import com.mxis.server.device.dto.DeviceResponse;
import com.mxis.server.device.dto.DeviceStatusUpdateRequest;
import com.mxis.server.device.entity.Device;
import com.mxis.server.device.repository.DeviceRepository;
import com.mxis.server.notification.service.PushNotificationService;
import com.mxis.server.product.entity.ProductDevice;
import com.mxis.server.product.repository.ProductDeviceRepository;
import com.mxis.server.user.entity.User;
import com.mxis.server.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeviceService {

    // ponytail: 배터리 부족 알림 임계값. 실측 후 조정 필요한 캘리브레이션 값.
    private static final int LOW_BATTERY_THRESHOLD = 20;

    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final ProductDeviceRepository productDeviceRepository;
    private final PushNotificationService pushNotificationService;

    @Transactional
    public DeviceResponse register(Long userId, DeviceRegisterRequest request) {
        if (deviceRepository.existsBySerialNumberAndDeletedAtIsNull(request.serialNumber())) {
            throw new BusinessException(ErrorCode.DEVICE_ALREADY_REGISTERED);
        }

        User user = userRepository.findActiveById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Device device = new Device(
                user,
                request.serialNumber(),
                request.deviceName(),
                request.macAddress(),
                request.firmwareVersion());

        return DeviceResponse.from(deviceRepository.save(device));
    }

    public List<DeviceResponse> getMyDevices(Long userId) {
        return deviceRepository.findAllActiveByUserId(userId).stream()
                .map(DeviceResponse::from)
                .toList();
    }

    public DeviceResponse getDevice(Long userId, Long deviceId) {
        return DeviceResponse.from(getOwnedDevice(userId, deviceId));
    }

    @Transactional
    public DeviceResponse updateStatus(Long userId, Long deviceId, DeviceStatusUpdateRequest request) {
        Device device = getOwnedDevice(userId, deviceId);
        device.updateStatus(request.connectionStatus(), request.batteryLevel(), null);
        notifyIfNeedsAttention(userId, device, request);
        return DeviceResponse.from(device);
    }

    /** 연결 끊김/오류 또는 배터리 부족으로 전환됐을 때만 알림 — 매 상태 갱신마다 보내면 스팸이 된다. */
    private void notifyIfNeedsAttention(Long userId, Device device, DeviceStatusUpdateRequest request) {
        if (request.connectionStatus() == DeviceConnectionStatus.DISCONNECTED
                || request.connectionStatus() == DeviceConnectionStatus.ERROR) {
            pushNotificationService.notifyUser(userId, NotificationType.DEVICE_STATUS,
                    "MXIS Charm 연결이 끊겼어요", "%s 연결을 다시 확인해 주세요.".formatted(device.getDeviceName()));
        } else if (request.batteryLevel() != null && request.batteryLevel() <= LOW_BATTERY_THRESHOLD) {
            pushNotificationService.notifyUser(userId, NotificationType.DEVICE_STATUS,
                    "배터리가 부족해요", "%s 배터리가 %d%% 남았어요.".formatted(device.getDeviceName(), request.batteryLevel()));
        }
    }

    /**
     * 페어링 전 기기 조회. 현재 스키마에는 "출고된 정품 Smart Charm 목록" 같은 별도 재고 테이블이 없으므로,
     * devices 테이블에 이미 등록된 일련번호인지만 확인한다. 즉 이 응답의 registrable=true는
     * "아직 아무도 등록하지 않은 일련번호"라는 의미이지, 정품 여부를 보증하지는 않는다.
     */
    public DeviceLookupResponse lookup(String serialNumber) {
        boolean alreadyRegistered = deviceRepository.existsBySerialNumberAndDeletedAtIsNull(serialNumber);
        return new DeviceLookupResponse(serialNumber, !alreadyRegistered);
    }

    @Transactional
    public void delete(Long userId, Long deviceId) {
        Device device = getOwnedDevice(userId, deviceId);

        // 기기를 삭제하면 연결돼 있던 제품과의 활성 링크도 함께 해제한다 (Product 쪽 소프트 삭제와 대칭되는 정합성 처리).
        for (ProductDevice link : productDeviceRepository.findActiveByDeviceId(deviceId)) {
            link.detach();
        }

        device.softDelete();
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
