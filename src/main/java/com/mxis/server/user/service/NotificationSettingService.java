package com.mxis.server.user.service;

import com.mxis.server.common.exception.BusinessException;
import com.mxis.server.common.exception.ErrorCode;
import com.mxis.server.user.dto.NotificationSettingResponse;
import com.mxis.server.user.dto.NotificationSettingUpdateRequest;
import com.mxis.server.user.entity.NotificationSetting;
import com.mxis.server.user.repository.NotificationSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationSettingService {

    private final NotificationSettingRepository notificationSettingRepository;

    public NotificationSettingResponse get(Long userId) {
        return NotificationSettingResponse.from(getEntity(userId));
    }

    @Transactional
    public NotificationSettingResponse update(Long userId, NotificationSettingUpdateRequest request) {
        NotificationSetting setting = getEntity(userId);
        setting.update(
                request.careTimingEnabled(),
                request.reservationEnabled(),
                request.deviceStatusEnabled(),
                request.marketingEnabled(),
                request.environmentAlertEnabled(),
                request.pushPermissionGranted(),
                request.pushToken());
        return NotificationSettingResponse.from(setting);
    }

    private NotificationSetting getEntity(Long userId) {
        // signup 시 항상 1행을 함께 생성하므로 없으면 데이터 정합성 문제로 간주한다.
        return notificationSettingRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "알림 설정 정보를 찾을 수 없습니다."));
    }
}
