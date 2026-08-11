package com.mxis.server.reservation.dto;

import com.mxis.server.common.enums.ReservationStatus;
import com.mxis.server.reservation.entity.Reservation;
import java.time.LocalDateTime;

/**
 * 취소 응답. 다른 도메인의 소프트 삭제(204 No Content)와 달리 취소 결과를 즉시 화면에
 * 반영해야 하는 UX라 200 + 갱신된 값을 반환한다 (확정 사항).
 */
public record ReservationCancelResponse(
        Long id,
        ReservationStatus status,
        LocalDateTime cancelledAt
) {
    public static ReservationCancelResponse from(Reservation r) {
        return new ReservationCancelResponse(r.getId(), r.getStatus(), r.getCancelledAt());
    }
}
