package com.mxis.server.reservation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mxis.server.common.enums.ReservationStatus;
import com.mxis.server.reservation.entity.Reservation;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 예약 생성/상세/변경 공통 응답.
 * ponytail: 명세상 생성 응답은 매장 주소·연락처를 포함하지 않지만, 필드가 더 있는 것은 클라이언트에
 * 무해하므로 상세 응답 하나로 합쳐 DTO 3개를 1개로 줄였다.
 */
public record ReservationResponse(
        Long id,
        Long productId,
        String productName,
        Long storeId,
        String storeName,
        String storeAddress,
        String storePhone,
        Long careSuggestionId,
        String serviceType,
        LocalDate reservedDate,
        @JsonFormat(pattern = "HH:mm") LocalTime reservedTime,
        String customerNote,
        ReservationStatus status,
        LocalDateTime cancelledAt,
        LocalDateTime completedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ReservationResponse from(Reservation r) {
        return new ReservationResponse(
                r.getId(),
                r.getProduct().getId(),
                r.getProduct().getProductName(),
                r.getStore().getId(),
                r.getStore().getStoreName(),
                r.getStore().getAddress(),
                r.getStore().getPhone(),
                r.getCareSuggestion() == null ? null : r.getCareSuggestion().getId(),
                r.getServiceType(),
                r.getReservedDate(),
                r.getReservedTime(),
                r.getCustomerNote(),
                r.getStatus(),
                r.getCancelledAt(),
                r.getCompletedAt(),
                r.getCreatedAt(),
                r.getUpdatedAt());
    }
}
