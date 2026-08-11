package com.mxis.server.care.service;

import com.mxis.server.care.entity.CareAlgorithm;
import com.mxis.server.care.entity.CareReport;
import com.mxis.server.care.entity.CareSuggestion;
import com.mxis.server.care.repository.CareAlgorithmRepository;
import com.mxis.server.care.repository.CareReportRepository;
import com.mxis.server.care.repository.CareSuggestionRepository;
import com.mxis.server.common.enums.CareConditionGrade;
import com.mxis.server.product.entity.Product;
import com.mxis.server.sensor.dto.SensorAggregate;
import com.mxis.server.sensor.repository.SensorReadingRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 센서 동기화 직후 진단 리포트(와 필요 시 케어 제안)를 새로 생성한다.
 *
 * 문구(summary/analysis/recommendation, message/reason)는 아직 LLM을 호출하지 않고 규칙 엔진의
 * 고정 폴백 문구를 저장한다. AI 연동 시 이 지점만 교체하면 되며, 호출 실패 시에도 지금 값이 남는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CareDiagnosisService {

    /** 리포트 스냅샷의 분석 대상 기간 (일). */
    static final int REPORT_PERIOD_DAYS = 30;

    private final CareAlgorithmRepository careAlgorithmRepository;
    private final CareReportRepository careReportRepository;
    private final CareSuggestionRepository careSuggestionRepository;
    private final SensorReadingRepository sensorReadingRepository;
    private final CareRuleEngine ruleEngine;

    /**
     * 진단 재계산. 센서 동기화 트랜잭션 안에서 호출되므로, 진단이 불가능한 상황(활성 알고리즘 없음,
     * 측정 데이터 없음)에서는 예외를 던지지 않고 조용히 건너뛴다 - 동기화 자체는 성공해야 한다.
     */
    @Transactional
    public void regenerate(Product product) {
        CareAlgorithm algorithm = careAlgorithmRepository.findByIsActiveTrue().orElse(null);
        if (algorithm == null) {
            log.warn("활성 care_algorithm이 없어 진단을 건너뜁니다. productId={}", product.getId());
            return;
        }

        LocalDateTime periodEnd = LocalDateTime.now();
        LocalDateTime periodStart = periodEnd.minusDays(REPORT_PERIOD_DAYS);

        SensorAggregate stats = sensorReadingRepository.aggregate(
                product.getId(), periodStart, periodEnd,
                CareRuleEngine.DRY_THRESHOLD, CareRuleEngine.STRONG_SHOCK_THRESHOLD);

        if (stats.isEmpty()) {
            log.debug("분석 기간 내 센서 데이터가 없어 진단을 건너뜁니다. productId={}", product.getId());
            return;
        }

        BigDecimal avgHumidity = scale(stats.avgHumidity());
        BigDecimal avgTemperature = scale(stats.avgTemperature());
        int shockCount = stats.shockCountAsInt();
        int outingCount = (int) sensorReadingRepository.countOutingSessions(
                product.getId(), periodStart, periodEnd);

        CareConditionGrade grade = ruleEngine.conditionGrade(
                ruleEngine.humidityGrade(avgHumidity), ruleEngine.shockGrade(shockCount));

        CareReport report = careReportRepository.save(new CareReport(
                product,
                algorithm,
                grade,
                ruleEngine.summaryText(grade),
                ruleEngine.analysisText(grade),
                ruleEngine.recommendationText(grade),
                periodStart,
                periodEnd,
                avgTemperature,
                stats.maxTemperature(),
                stats.minTemperature(),
                avgHumidity,
                outingCount,
                shockCount));

        if (ruleEngine.needsSuggestion(grade)) {
            createSuggestion(product, report, grade);
        }
    }

    private void createSuggestion(Product product, CareReport report, CareConditionGrade grade) {
        // 제안이 계속 쌓이면 "활성 제안"이 모호해지므로 이전 활성 제안은 만료 처리한다.
        List<CareSuggestion> previous = careSuggestionRepository.findActiveByProductId(product.getId());
        previous.forEach(CareSuggestion::expire);

        LocalDate visitFrom = LocalDate.now().plusDays(4);
        LocalDate visitTo = visitFrom.plusMonths(1);

        careSuggestionRepository.save(new CareSuggestion(
                report,
                product,
                ruleEngine.suggestionMessage(grade),
                ruleEngine.suggestionReason(grade),
                ruleEngine.recommendedService(grade),
                visitFrom,
                visitTo,
                visitTo.atTime(23, 59, 59)));
    }

    private static BigDecimal scale(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
