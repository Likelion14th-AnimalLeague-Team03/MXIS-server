package com.mxis.server.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.mxis.server.reservation.entity.Reservation;
import com.mxis.server.reservation.repository.ReservationRepository;
import com.mxis.server.store.entity.Store;
import com.mxis.server.store.repository.StoreRepository;
import com.mxis.server.user.entity.User;
import com.mxis.server.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReservationServiceTest {

    private final ReservationRepository reservationRepository = mock(ReservationRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final CareSuggestionRepository careSuggestionRepository = mock(CareSuggestionRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ReservationService service = new ReservationService(
            reservationRepository, productRepository, storeRepository, careSuggestionRepository, userRepository);

    private final LocalDate reservedDate = LocalDate.now().plusDays(7);
    private final LocalTime reservedTime = LocalTime.of(14, 0);
    private User user;
    private Product product;
    private Store store;

    @BeforeEach
    void setUp() {
        user = mock(User.class);
        product = mock(Product.class);
        store = mock(Store.class);

        when(user.getId()).thenReturn(1L);
        when(product.getId()).thenReturn(20L);
        when(product.getProductName()).thenReturn("MCM Aren Shopper");
        when(product.isOwnedBy(1L)).thenReturn(true);
        when(store.getId()).thenReturn(3L);
        when(store.getStoreName()).thenReturn("MCM 청담 플래그십");
        when(store.isBookableAt(reservedTime)).thenReturn(true);

        when(userRepository.findActiveById(1L)).thenReturn(Optional.of(user));
        when(productRepository.findActiveById(20L)).thenReturn(Optional.of(product));
        when(storeRepository.findByIdAndIsActiveTrue(3L)).thenReturn(Optional.of(store));
        when(reservationRepository.existsConfirmedSlot(3L, reservedDate, reservedTime, -1L)).thenReturn(false);
    }

    @Test
    void create_freeReservation_isConfirmedImmediately() {
        when(reservationRepository.existsActiveByUserIdAndReservationType(1L, ReservationType.FREE))
                .thenReturn(false);

        ReservationResponse response = service.create(1L, request(ReservationType.FREE));

        assertThat(response.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(response.reservationType()).isEqualTo(ReservationType.FREE);
        verify(reservationRepository).saveAndFlush(any(Reservation.class));
    }

    @Test
    void create_paidReservation_waitsForApproval() {
        when(reservationRepository.existsActiveByUserIdAndReservationType(1L, ReservationType.PAID))
                .thenReturn(false);

        ReservationResponse response = service.create(1L, request(ReservationType.PAID));

        assertThat(response.status()).isEqualTo(ReservationStatus.PENDING_APPROVAL);
        assertThat(response.reservationType()).isEqualTo(ReservationType.PAID);
    }

    @Test
    void create_sameActiveReservationType_throwsConflict() {
        when(reservationRepository.existsActiveByUserIdAndReservationType(1L, ReservationType.PAID))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(1L, request(ReservationType.PAID)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESERVATION_TYPE_ALREADY_EXISTS);
    }

    @Test
    void pendingApprovalReservation_canBeCancelled() {
        Reservation reservation = new Reservation(
                user, product, store, null, "케어 컨시어지", ReservationType.PAID,
                reservedDate, reservedTime, "상담 요청");
        when(reservationRepository.findById(100L)).thenReturn(Optional.of(reservation));

        ReservationCancelResponse response = service.cancel(1L, 100L);

        assertThat(response.status()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(response.cancelledAt()).isNotNull();
    }

    private ReservationCreateRequest request(ReservationType reservationType) {
        return new ReservationCreateRequest(
                20L, 3L, null, reservationType == ReservationType.PAID ? "케어 컨시어지" : "무상케어",
                reservationType, reservedDate, reservedTime, "요청사항");
    }
}
