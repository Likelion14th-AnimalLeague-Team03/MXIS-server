package com.mxis.server.home.service;

import com.mxis.server.care.entity.CareReport;
import com.mxis.server.care.entity.CareSuggestion;
import com.mxis.server.care.repository.CareReportRepository;
import com.mxis.server.care.repository.CareSuggestionRepository;
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
import java.time.Clock;
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
    private final Clock clock;

    public HomeResponse getHome(Long userId, Long productId) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
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
        } else if (!"SUFFICIENT".equals(report.getDataStatus()) && !"STALE_DATA".equals(report.getDataStatus())) {
            state = HomeResponse.ProductState.COLLECTING;
            headline = report.getSummaryText() == null
                    ? "정확한 분석을 위해 센서 데이터를 수집하고 있습니다." : report.getSummaryText();
        } else if ("STALE_DATA".equals(report.getDataStatus()) || device == null || device.getLastSyncedAt() == null
                || device.getLastSyncedAt().isBefore(now.minusDays(STALE_SYNC_DAYS))
                || report.getPeriodEnd() == null || report.getPeriodEnd().isBefore(now.minusDays(STALE_SYNC_DAYS))) {
            state = HomeResponse.ProductState.NEEDS_UPDATE;
            headline = "새로운 케어 데이터를 기다리고 있어요. 정확한 케어 상태를 확인하려면 Charm을 연동해주세요.";
        } else {
            state = report.getConditionScore() == null ? HomeResponse.ProductState.COLLECTING : HomeResponse.ProductState.NORMAL;
            score = report.getConditionScore();
            headline = careSuggestionRepository.findLatestActiveByProductId(productId, now)
                    .map(CareSuggestion::getMessage)
                    .orElse(report.getSummaryText());
        }

        Reservation upcoming = reservationRepository
                .findFirstByProductIdAndStatusAndReservedDateGreaterThanEqualOrderByReservedDateAscReservedTimeAsc(
                        productId, ReservationStatus.CONFIRMED, today)
                .orElse(null);

        boolean charmNeedsReconnect = device == null || device.getConnectionStatus() != DeviceConnectionStatus.CONNECTED;

        return new HomeResponse(
                user.getName(),
                product.getProductImageUrl(),
                state,
                score,
                headline,
                daysTogether(product, today),
                upcoming == null ? null : new HomeResponse.UpcomingReservation(
                        upcoming.getId(),
                        (int) ChronoUnit.DAYS.between(today, upcoming.getReservedDate()),
                        upcoming.getReservedDate(),
                        upcoming.getReservedTime(),
                        upcoming.getStore().getStoreName()),
                charmNeedsReconnect);
    }

    /** products.purchased_at(없으면 등록일) 기준 함께한 일수. */
    static int daysTogether(Product product, LocalDate today) {
        LocalDate since = product.getPurchasedAt() != null
                ? product.getPurchasedAt()
                : product.getRegisteredAt().toLocalDate();
        return (int) Math.max(0, ChronoUnit.DAYS.between(since, today));
    }
}
