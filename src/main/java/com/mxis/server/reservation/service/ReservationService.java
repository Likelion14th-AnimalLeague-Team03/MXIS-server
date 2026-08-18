package com.mxis.server.reservation.service;

import com.mxis.server.care.entity.CareSuggestion;
import com.mxis.server.care.repository.CareSuggestionRepository;
import com.mxis.server.common.enums.ReservationStatus;
import com.mxis.server.common.enums.ReservationType;
import com.mxis.server.common.exception.BusinessException;
import com.mxis.server.common.exception.ErrorCode;
import com.mxis.server.product.entity.Product;
import com.mxis.server.product.repository.ProductRepository;
import com.mxis.server.reservation.dto.ReservationCancelResponse;
import com.mxis.server.reservation.dto.ReservationCreateRequest;
import com.mxis.server.reservation.dto.ReservationResponse;
import com.mxis.server.reservation.dto.ReservationSummaryResponse;
import com.mxis.server.reservation.dto.ReservationUpdateRequest;
import com.mxis.server.reservation.entity.Reservation;
import com.mxis.server.reservation.repository.ReservationRepository;
import com.mxis.server.store.entity.Store;
import com.mxis.server.store.repository.StoreRepository;
import com.mxis.server.user.entity.User;
import com.mxis.server.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationService {

    /** 신규 생성 시에는 제외할 자기 자신이 없으므로 존재할 수 없는 id를 넘긴다. */
    private static final long NO_EXCLUSION = -1L;

    private final ReservationRepository reservationRepository;
    private final ProductRepository productRepository;
    private final StoreRepository storeRepository;
    private final CareSuggestionRepository careSuggestionRepository;
    private final UserRepository userRepository;

    @Transactional
    public ReservationResponse create(Long userId, ReservationCreateRequest request) {
        User user = userRepository.findActiveById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Product product = getOwnedProduct(userId, request.productId());
        Store store = storeRepository.findByIdAndIsActiveTrue(request.storeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));

        ensureNoActiveReservationOfSameType(userId, request.reservationType());
        validateSlot(store, request.reservedDate(), request.reservedTime());
        ensureSlotFree(store.getId(), request.reservedDate(), request.reservedTime(), NO_EXCLUSION);

        CareSuggestion suggestion = resolveSuggestion(request.careSuggestionId(), product.getId());

        Reservation reservation = new Reservation(
                user, product, store, suggestion,
                request.serviceType(), request.reservationType(),
                request.reservedDate(), request.reservedTime(), request.customerNote());

        save(reservation);

        // 제안에서 이어진 예약이면 제안 상태를 RESERVED로 전환해 "제안 -> 예약" 전환을 추적한다.
        if (suggestion != null) {
            suggestion.markReserved();
        }

        return ReservationResponse.from(reservation);
    }

    public List<ReservationSummaryResponse> getMyReservations(Long userId, ReservationStatus status) {
        return reservationRepository.findAllByUser(userId, status).stream()
                .map(ReservationSummaryResponse::from)
                .toList();
    }

    public ReservationResponse getReservation(Long userId, Long reservationId) {
        return ReservationResponse.from(getOwnedReservation(userId, reservationId));
    }

    @Transactional
    public ReservationResponse update(Long userId, Long reservationId, ReservationUpdateRequest request) {
        Reservation reservation = getOwnedReservation(userId, reservationId);
        if (!reservation.isModifiable()) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_MODIFIABLE);
        }

        LocalDate newDate = request.reservedDate() == null ? reservation.getReservedDate() : request.reservedDate();
        LocalTime newTime = request.reservedTime() == null ? reservation.getReservedTime() : request.reservedTime();

        if (request.reservedDate() != null || request.reservedTime() != null) {
            validateSlot(reservation.getStore(), newDate, newTime);
            // 자기 자신은 슬롯 점유 판정에서 제외해야 한다 (시간을 바꾸지 않는 요청도 통과해야 하므로).
            ensureSlotFree(reservation.getStore().getId(), newDate, newTime, reservation.getId());
        }

        reservation.reschedule(request.reservedDate(), request.reservedTime(), request.customerNote());
        flushSlotConstraint();

        return ReservationResponse.from(reservation);
    }

    @Transactional
    public ReservationCancelResponse cancel(Long userId, Long reservationId) {
        Reservation reservation = getOwnedReservation(userId, reservationId);
        if (!reservation.isModifiable()) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_MODIFIABLE);
        }
        reservation.cancel();
        return ReservationCancelResponse.from(reservation);
    }

    /** 예약 가능 시간대는 매장마다 다르므로 해당 매장의 슬롯 목록으로 검증한다. */
    private void validateSlot(Store store, LocalDate date, LocalTime time) {
        if (date.isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지난 날짜로는 예약할 수 없습니다.");
        }
        if (!store.isBookableAt(time)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "%s 예약은 %s~%s 사이 30분 단위로만 가능합니다."
                            .formatted(store.getStoreName(), store.getOpenTime(), store.getCloseTime()));
        }
    }

    /**
     * 사전 점유 확인. 동시 요청은 이 검사를 통과할 수 있으므로 최종 방어선은 DB의
     * uq_confirmed_reservation_slot 유니크 제약이며, 저장 시 제약 위반을 같은 에러코드로 변환한다.
     */
    private void ensureSlotFree(Long storeId, LocalDate date, LocalTime time, Long excludeId) {
        if (reservationRepository.existsConfirmedSlot(storeId, date, time, excludeId)) {
            throw new BusinessException(ErrorCode.SLOT_ALREADY_RESERVED);
        }
    }

    private void ensureNoActiveReservationOfSameType(Long userId, ReservationType reservationType) {
        if (!reservationRepository.existsActiveByUserIdAndReservationType(userId, reservationType)) {
            return;
        }
        String message = reservationType == ReservationType.PAID
                ? "이미 케어 컨시어지 예약이 있습니다."
                : "이미 무상케어 예약이 있습니다.";
        throw new BusinessException(ErrorCode.RESERVATION_TYPE_ALREADY_EXISTS, message);
    }

    private void save(Reservation reservation) {
        try {
            reservationRepository.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.SLOT_ALREADY_RESERVED);
        }
    }

    private void flushSlotConstraint() {
        try {
            reservationRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.SLOT_ALREADY_RESERVED);
        }
    }

    private CareSuggestion resolveSuggestion(Long careSuggestionId, Long productId) {
        if (careSuggestionId == null) {
            return null;
        }
        CareSuggestion suggestion = careSuggestionRepository.findById(careSuggestionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CARE_SUGGESTION_NOT_FOUND));
        if (!suggestion.getProduct().getId().equals(productId)) {
            throw new BusinessException(ErrorCode.CARE_SUGGESTION_NOT_OWNED);
        }
        return suggestion;
    }

    private Product getOwnedProduct(Long userId, Long productId) {
        Product product = productRepository.findActiveById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        if (!product.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_OWNED);
        }
        return product;
    }

    private Reservation getOwnedReservation(Long userId, Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        if (!reservation.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_OWNED);
        }
        return reservation;
    }
}
