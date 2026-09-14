package com.mxis.server.care.entity;

import com.mxis.server.common.entity.BaseCreatedAtEntity;
import com.mxis.server.care.dto.AiCareSummaryResponse;
import java.time.temporal.ChronoUnit;
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
import java.time.format.DateTimeFormatter;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 진단 리포트 스냅샷. Immutable - INSERT 이후 수정하지 않고, 재진단은 새 행으로 쌓는다.
 * 분석 기간별 판단·점수·입력 버전을 보존한다. 기본 케어 화면과 제안은 30일 스냅샷을 사용한다.
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

    @Column(name = "analysis_window_days", nullable = false)
    private int analysisWindowDays;

    @Column(name = "data_status", nullable = false, length = 30)
    private String dataStatus;

    @Column(name = "condition_label", length = 30)
    private String conditionLabel;

    @Column(name = "condition_score")
    private Integer conditionScore;

    @Column(name = "primary_factor", length = 50)
    private String primaryFactor;

    @Column(name = "care_need", length = 30)
    private String careNeed;

    @Column(name = "inspection_need", length = 30)
    private String inspectionNeed;

    @Column(name = "sensor_revision")
    private Long sensorRevision;

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

    @Column(name = "ai_output", nullable = false, columnDefinition = "JSON")
    private String aiOutput;

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
        this.analysisWindowDays = (int) ChronoUnit.DAYS.between(periodStart, periodEnd);
        this.dataStatus = "INSUFFICIENT_DATA";
        this.conditionLabel = "Collecting Data";
        this.avgTemperature = avgTemperature;
        this.maxTemperature = maxTemperature;
        this.minTemperature = minTemperature;
        this.avgHumidity = avgHumidity;
        this.outingCount = outingCount;
        this.shockCount = shockCount;
        this.aiOutput = minimalAiOutput(conditionGrade, summaryText, periodStart, periodEnd);
    }

    public CareReport(Product product, CareAlgorithm algorithm, CareConditionGrade conditionGrade,
                      String summaryText, String analysisText, String recommendationText,
                      LocalDateTime periodStart, LocalDateTime periodEnd,
                      BigDecimal avgTemperature, BigDecimal maxTemperature, BigDecimal minTemperature,
                      BigDecimal avgHumidity, Integer outingCount, Integer shockCount, String aiOutput) {
        this.product = product;
        this.algorithm = algorithm;
        this.conditionGrade = conditionGrade;
        this.summaryText = summaryText;
        this.analysisText = analysisText;
        this.recommendationText = recommendationText;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.analysisWindowDays = (int) ChronoUnit.DAYS.between(periodStart, periodEnd);
        this.dataStatus = "INSUFFICIENT_DATA";
        this.conditionLabel = "Collecting Data";
        this.avgTemperature = avgTemperature;
        this.maxTemperature = maxTemperature;
        this.minTemperature = minTemperature;
        this.avgHumidity = avgHumidity;
        this.outingCount = outingCount;
        this.shockCount = shockCount;
        this.aiOutput = aiOutput == null || aiOutput.isBlank()
                ? minimalAiOutput(conditionGrade, summaryText, periodStart, periodEnd)
                : aiOutput;
    }

    public CareReport(Product product, CareAlgorithm algorithm, CareConditionGrade conditionGrade,
                      String summaryText, String analysisText, String recommendationText,
                      LocalDateTime periodStart, LocalDateTime periodEnd,
                      BigDecimal avgTemperature, BigDecimal maxTemperature, BigDecimal minTemperature,
                      BigDecimal avgHumidity, Integer outingCount, Integer shockCount, String aiOutput,
                      AiCareSummaryResponse summary, String careNeed, String inspectionNeed, Long sensorRevision) {
        this(product, algorithm, conditionGrade, summaryText, analysisText, recommendationText,
                periodStart, periodEnd, avgTemperature, maxTemperature, minTemperature,
                avgHumidity, outingCount, shockCount, aiOutput);
        this.analysisWindowDays = summary.analysisWindowDays();
        this.dataStatus = summary.dataSufficiency().status();
        this.conditionLabel = summary.productCondition().label();
        this.conditionScore = "SUFFICIENT".equals(dataStatus) && conditionGrade != CareConditionGrade.COLLECTING_DATA
                ? summary.productCondition().score() : null;
        this.primaryFactor = summary.productCondition().primaryFactor();
        this.careNeed = careNeed;
        this.inspectionNeed = inspectionNeed;
        this.sensorRevision = sensorRevision;
    }

    private static String minimalAiOutput(CareConditionGrade conditionGrade, String summaryText,
                                          LocalDateTime periodStart, LocalDateTime periodEnd) {
        return """
                {"schemaVersion":"care-report-snapshot-v0.1","productCondition":{"grade":"%s","summary":"%s"},"analysisWindow":{"periodStart":"%s","periodEnd":"%s"}}
                """.formatted(
                conditionGrade.name(),
                escapeJson(summaryText),
                periodStart.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                periodEnd.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).trim();
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
