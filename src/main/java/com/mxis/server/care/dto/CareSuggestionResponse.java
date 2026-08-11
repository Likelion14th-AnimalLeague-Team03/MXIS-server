package com.mxis.server.care.dto;

import com.mxis.server.care.entity.CareSuggestion;
import com.mxis.server.common.enums.CareSuggestionStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 활성 제안 조회와 제안 상세 조회 공통 응답.
 * ponytail: 명세상 활성 제안 응답은 productId/status/isRead를 포함하지 않지만, 필드가 더 있는 것은
 * 클라이언트에 무해하므로 DTO 2개를 1개로 합쳤다.
 */
public record CareSuggestionResponse(
        Long id,
        Long productId,
        String message,
        String reasonText,
        String recommendedService,
        LocalDate recommendedVisitFrom,
        LocalDate recommendedVisitTo,
        CareSuggestionStatus status,
        boolean isRead,
        LocalDateTime createdAt
) {
    public static CareSuggestionResponse from(CareSuggestion s) {
        return new CareSuggestionResponse(
                s.getId(),
                s.getProduct().getId(),
                s.getMessage(),
                s.getReasonText(),
                s.getRecommendedService(),
                s.getRecommendedVisitFrom(),
                s.getRecommendedVisitTo(),
                s.getStatus(),
                s.isRead(),
                s.getCreatedAt());
    }

    /** 읽음 처리 응답 (id + isRead만). */
    public record ReadResult(Long id, boolean isRead) {
        public static ReadResult from(CareSuggestion s) {
            return new ReadResult(s.getId(), s.isRead());
        }
    }
}
