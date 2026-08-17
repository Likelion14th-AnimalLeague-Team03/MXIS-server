package com.mxis.server.home.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import java.time.LocalTime;

/** CM-100 메인 홈 화면 전용 응답. 여러 도메인(제품/기기/진단/예약)을 화면이 필요로 하는 모양 그대로 조합한다. */
public record HomeResponse(
        String userName,
        String productImageUrl,
        ProductState productState,
        Integer score,
        String headline,
        int daysTogether,
        UpcomingReservation upcomingReservation,
        boolean charmNeedsReconnect
) {
    /** COLLECTING: 진단 리포트 없음(등록 직후). NEEDS_UPDATE: 리포트는 있으나 최근 동기화 없음. NORMAL: 정상 진단 상태. */
    public enum ProductState {
        COLLECTING, NEEDS_UPDATE, NORMAL
    }

    public record UpcomingReservation(
            Long reservationId,
            int dDay,
            LocalDate reservedDate,
            @JsonFormat(pattern = "HH:mm") LocalTime reservedTime,
            String storeName) {
    }
}
