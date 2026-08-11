package com.mxis.server.reservation.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mxis.server.common.enums.ReservationStatus;
import com.mxis.server.reservation.entity.Reservation;
import java.time.LocalDate;
import java.time.LocalTime;

/** 목록 조회용 축약 응답. */
public record ReservationSummaryResponse(
        Long id,
        Long productId,
        String productName,
        Long storeId,
        String storeName,
        LocalDate reservedDate,
        @JsonFormat(pattern = "HH:mm") LocalTime reservedTime,
        ReservationStatus status
) {
    public static ReservationSummaryResponse from(Reservation r) {
        return new ReservationSummaryResponse(
                r.getId(),
                r.getProduct().getId(),
                r.getProduct().getProductName(),
                r.getStore().getId(),
                r.getStore().getStoreName(),
                r.getReservedDate(),
                r.getReservedTime(),
                r.getStatus());
    }
}
