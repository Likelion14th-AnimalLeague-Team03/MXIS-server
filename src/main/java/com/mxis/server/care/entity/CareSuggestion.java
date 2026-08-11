package com.mxis.server.care.entity;

import com.mxis.server.common.entity.BaseTimeEntity;
import com.mxis.server.common.enums.CareSuggestionStatus;
import com.mxis.server.product.entity.Product;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 케어 제안. CareReport 1건당 최대 1개(care_report_id UNIQUE)이며,
 * 종합 등급이 LIGHT_CARE 이상일 때만 생성된다.
 */
@Getter
@Entity
@Table(name = "care_suggestions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CareSuggestion extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "care_report_id", nullable = false, unique = true)
    private CareReport careReport;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "reason_text", columnDefinition = "TEXT")
    private String reasonText;

    @Column(name = "recommended_service", length = 100)
    private String recommendedService;

    @Column(name = "recommended_visit_from")
    private LocalDate recommendedVisitFrom;

    @Column(name = "recommended_visit_to")
    private LocalDate recommendedVisitTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CareSuggestionStatus status = CareSuggestionStatus.ACTIVE;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    public CareSuggestion(CareReport careReport, Product product, String message, String reasonText,
                          String recommendedService, LocalDate recommendedVisitFrom, LocalDate recommendedVisitTo,
                          LocalDateTime expiresAt) {
        this.careReport = careReport;
        this.product = product;
        this.message = message;
        this.reasonText = reasonText;
        this.recommendedService = recommendedService;
        this.recommendedVisitFrom = recommendedVisitFrom;
        this.recommendedVisitTo = recommendedVisitTo;
        this.expiresAt = expiresAt;
        this.status = CareSuggestionStatus.ACTIVE;
    }

    public void markRead() {
        this.isRead = true;
    }

    public void markReserved() {
        this.status = CareSuggestionStatus.RESERVED;
    }

    public void expire() {
        this.status = CareSuggestionStatus.EXPIRED;
    }
}
