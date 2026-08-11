package com.mxis.server.reservation.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.format.annotation.DateTimeFormat;

/** 모든 필드가 선택 - null이면 해당 항목은 변경하지 않는다. */
public record ReservationUpdateRequest(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reservedDate,
        @DateTimeFormat(pattern = "HH:mm") LocalTime reservedTime,
        String customerNote
) {
}
