package com.mxis.server.care.entity;

import com.mxis.server.common.entity.BaseCreatedAtEntity;
import com.mxis.server.common.enums.CareConditionGrade;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 진단 리포트 스냅샷. Immutable - INSERT 이후 수정하지 않고, 재진단은 새 행으로 쌓는다.
 * 30일 핵심 지표만 저장하며, 7일 건조노출·함께한시간 등은 조회 시점에 실시간 계산한다.
 */
@Getter
@Entity
@Table(name = "care_reports")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CareReport extends BaseCreatedAtEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "algorithm_id", nullable = false)
    private CareAlgorithm algorithm;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_grade", nullable = false, length = 20)
    private CareConditionGrade conditionGrade;

    @Column(name = "summary_text", nullable = false, columnDefinition = "TEXT")
    private String summaryText;

    @Column(name = "analysis_text", columnDefinition = "TEXT")
    private String analysisText;

    @Column(name = "recommendation_text", columnDefinition = "TEXT")
    private String recommendationText;

    @Column(name = "period_start", nullable = false)
    private LocalDateTime periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDateTime periodEnd;

    @Column(name = "avg_temperature", precision = 5, scale = 2)
    private BigDecimal avgTemperature;

    @Column(name = "max_temperature", precision = 5, scale = 2)
    private BigDecimal maxTemperature;

    @Column(name = "min_temperature", precision = 5, scale = 2)
    private BigDecimal minTemperature;

    @Column(name = "avg_humidity", precision = 5, scale = 2)
    private BigDecimal avgHumidity;

    @Column(name = "outing_count")
    private Integer outingCount;

    @Column(name = "shock_count")
    private Integer shockCount;

    public CareReport(Product product, CareAlgorithm algorithm, CareConditionGrade conditionGrade,
                      String summaryText, String analysisText, String recommendationText,
                      LocalDateTime periodStart, LocalDateTime periodEnd,
                      BigDecimal avgTemperature, BigDecimal maxTemperature, BigDecimal minTemperature,
                      BigDecimal avgHumidity, Integer outingCount, Integer shockCount) {
        this.product = product;
        this.algorithm = algorithm;
        this.conditionGrade = conditionGrade;
        this.summaryText = summaryText;
        this.analysisText = analysisText;
        this.recommendationText = recommendationText;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.avgTemperature = avgTemperature;
        this.maxTemperature = maxTemperature;
        this.minTemperature = minTemperature;
        this.avgHumidity = avgHumidity;
        this.outingCount = outingCount;
        this.shockCount = shockCount;
    }
}
