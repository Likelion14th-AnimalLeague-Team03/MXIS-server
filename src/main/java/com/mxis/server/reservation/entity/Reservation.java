package com.mxis.server.reservation.entity;

import com.mxis.server.care.entity.CareSuggestion;
import com.mxis.server.common.entity.BaseTimeEntity;
import com.mxis.server.common.enums.ReservationStatus;
import com.mxis.server.common.enums.ReservationType;
import com.mxis.server.product.entity.Product;
import com.mxis.server.store.entity.Store;
import com.mxis.server.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 매장 방문 예약.
 * active_slot_key (Virtual Generated Column, DB에서만 존재)로
 * "(store, date, time)당 CONFIRMED 예약 최대 1개" 제약을 강제한다 - 엔티티에는 매핑하지 않는다.
 */
@Getter
@Entity
@Table(name = "reservations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "care_suggestion_id")
    private CareSuggestion careSuggestion;

    @Column(name = "service_type", length = 100)
    private String serviceType;

    @Enumerated(EnumType.STRING)
    @Column(name = "reservation_type", nullable = false, length = 10)
    private ReservationType reservationType;

    @Column(name = "reserved_date", nullable = false)
    private LocalDate reservedDate;

    @Column(name = "reserved_time", nullable = false)
    private LocalTime reservedTime;

    @Column(name = "customer_note", columnDefinition = "TEXT")
    private String customerNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status = ReservationStatus.CONFIRMED;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public Reservation(User user, Product product, Store store, CareSuggestion careSuggestion,
                       String serviceType, ReservationType reservationType,
                       LocalDate reservedDate, LocalTime reservedTime, String customerNote) {
        this.user = user;
        this.product = product;
        this.store = store;
        this.careSuggestion = careSuggestion;
        this.serviceType = serviceType;
        this.reservationType = reservationType;
        this.reservedDate = reservedDate;
        this.reservedTime = reservedTime;
        this.customerNote = customerNote;
        this.status = reservationType == ReservationType.PAID
                ? ReservationStatus.PENDING_APPROVAL
                : ReservationStatus.CONFIRMED;
    }

    public boolean isOwnedBy(Long userId) {
        return this.user.getId().equals(userId);
    }

    /** 취소/완료된 예약은 더 이상 변경할 수 없다. */
    public boolean isModifiable() {
        return status == ReservationStatus.CONFIRMED
                || status == ReservationStatus.PENDING_APPROVAL;
    }

    public void reschedule(LocalDate reservedDate, LocalTime reservedTime, String customerNote) {
        if (reservedDate != null) {
            this.reservedDate = reservedDate;
        }
        if (reservedTime != null) {
            this.reservedTime = reservedTime;
        }
        if (customerNote != null) {
            this.customerNote = customerNote;
        }
    }

    public void cancel() {
        this.status = ReservationStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }
}
