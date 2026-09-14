package com.mxis.server.product.service;

import com.mxis.server.common.exception.BusinessException;
import com.mxis.server.common.exception.ErrorCode;
import com.mxis.server.device.entity.Device;
import com.mxis.server.device.repository.DeviceRepository;
import com.mxis.server.user.entity.User;
import com.mxis.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Topology mutations take the owner row first, then device rows in ascending ID order.
 * Sensor ingestion follows the same order, so history cannot change while it is attributed.
 * All locks live only for the short caller database transaction; no network work belongs here.
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class ProductDeviceMutationLock {
    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;

    public User lockOwner(Long userId) {
        return userRepository.findActiveByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    public Device lockOwnedDevice(Long userId, Long deviceId) {
        Device device = deviceRepository.findActiveByIdForUpdate(deviceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DEVICE_NOT_FOUND));
        if (!device.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.DEVICE_NOT_OWNED);
        }
        return device;
    }
}
