package com.mxis.server.reservation.dto;

import com.mxis.server.common.enums.ReservationType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.format.annotation.DateTimeFormat;

public record ReservationCreateRequest(
        @NotNull Long productId,
        @NotNull Long storeId,
        Long careSuggestionId,
        @Size(max = 100) String serviceType,
        @NotNull ReservationType reservationType,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reservedDate,
        @NotNull @DateTimeFormat(pattern = "HH:mm") LocalTime reservedTime,
        String customerNote
) {
}
