package com.mxis.server.home.service;

import com.mxis.server.care.entity.CareReport;
import com.mxis.server.care.entity.CareSuggestion;
import com.mxis.server.care.repository.CareReportRepository;
import com.mxis.server.care.repository.CareSuggestionRepository;
import com.mxis.server.care.service.CareRuleEngine;
import com.mxis.server.common.enums.CareConditionGrade;
import com.mxis.server.common.enums.DeviceConnectionStatus;
import com.mxis.server.common.enums.ReservationStatus;
import com.mxis.server.common.exception.BusinessException;
import com.mxis.server.common.exception.ErrorCode;
import com.mxis.server.device.entity.Device;
import com.mxis.server.home.dto.HomeResponse;
import com.mxis.server.product.entity.Product;
import com.mxis.server.product.entity.ProductDevice;
import com.mxis.server.product.repository.ProductDeviceRepository;
import com.mxis.server.product.repository.ProductRepository;
import com.mxis.server.reservation.entity.Reservation;
import com.mxis.server.reservation.repository.ReservationRepository;
import com.mxis.server.user.entity.User;
import com.mxis.server.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeService {

    // ponytail: 기기 마지막 동기화가 이 기간을 넘으면 "업데이트 필요"로 본다. 캘리브레이션 값 — 실측 후 조정.
    private static final int STALE_SYNC_DAYS = 3;

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductDeviceRepository productDeviceRepository;
    private final CareReportRepository careReportRepository;
    private final CareSuggestionRepository careSuggestionRepository;
    private final ReservationRepository reservationRepository;
    private final CareRuleEngine ruleEngine;

    public HomeResponse getHome(Long userId, Long productId) {
        User user = userRepository.findActiveById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Product product = productRepository.findActiveById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        if (!product.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_OWNED);
        }

        Device device = productDeviceRepository.findActivePrimaryByProductId(productId)
                .map(ProductDevice::getDevice)
                .orElse(null);
        CareReport report = careReportRepository.findFirstByProductIdOrderByCreatedAtDesc(productId).orElse(null);

        HomeResponse.ProductState state;
        Integer score = null;
        String headline;

        if (report == null) {
            state = HomeResponse.ProductState.COLLECTING;
            headline = "Charm과 함께 제품을 사용하면 환경과 사용 기록이 차곡차곡 쌓입니다.";
        } else if (device == null || device.getLastSyncedAt() == null
                || device.getLastSyncedAt().isBefore(LocalDateTime.now().minusDays(STALE_SYNC_DAYS))) {
            state = HomeResponse.ProductState.NEEDS_UPDATE;
            headline = "새로운 케어 데이터를 기다리고 있어요. 정확한 케어 상태를 확인하려면 Charm을 연동해주세요.";
        } else {
            state = HomeResponse.ProductState.NORMAL;
            CareConditionGrade grade = report.getConditionGrade();
            score = toScore(grade);
            // TODO(파이썬 AI 서비스 연동 예정): 지금은 룰 기반 문구로 대체.
            // LIGHT_CARE 이상 등급만 CareSuggestion이 존재하므로, 없으면 등급 요약 문구로 폴백.
            headline = careSuggestionRepository.findLatestActiveByProductId(productId)
                    .map(CareSuggestion::getMessage)
                    .orElse(ruleEngine.summaryText(grade));
        }

        Reservation upcoming = reservationRepository
                .findFirstByProductIdAndStatusAndReservedDateGreaterThanEqualOrderByReservedDateAscReservedTimeAsc(
                        productId, ReservationStatus.CONFIRMED, LocalDate.now())
                .orElse(null);

        boolean charmNeedsReconnect = device == null || device.getConnectionStatus() != DeviceConnectionStatus.CONNECTED;

        return new HomeResponse(
                user.getName(),
                product.getProductImageUrl(),
                state,
                score,
                headline,
                daysTogether(product),
                upcoming == null ? null : new HomeResponse.UpcomingReservation(
                        upcoming.getId(),
                        (int) ChronoUnit.DAYS.between(LocalDate.now(), upcoming.getReservedDate()),
                        upcoming.getReservedDate(),
                        upcoming.getReservedTime(),
                        upcoming.getStore().getStoreName()),
                charmNeedsReconnect);
    }

    // ponytail: 등급당 실제 점수가 없어 25% 단위 4단계로 균등 매핑. 실측 근거 생기면 조정.
    static int toScore(CareConditionGrade grade) {
        return switch (grade) {
            case STABLE -> 100;
            case BALANCED -> 75;
            case LIGHT_CARE -> 50;
            case EXPERT_CHECK -> 25;
        };
    }

    /** products.purchased_at(없으면 등록일) 기준 함께한 일수. */
    static int daysTogether(Product product) {
        LocalDate since = product.getPurchasedAt() != null
                ? product.getPurchasedAt()
                : product.getRegisteredAt().toLocalDate();
        return (int) Math.max(0, ChronoUnit.DAYS.between(since, LocalDate.now()));
    }
}
