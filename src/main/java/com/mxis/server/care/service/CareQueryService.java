package com.mxis.server.care.service;

import com.mxis.server.care.dto.CareDashboardResponse;
import com.mxis.server.care.dto.CareReportResponse;
import com.mxis.server.care.dto.SensorPeriod;
import com.mxis.server.care.dto.SensorSummaryResponse;
import com.mxis.server.care.entity.CareReport;
import com.mxis.server.care.entity.CareSuggestion;
import com.mxis.server.care.repository.CareReportRepository;
import com.mxis.server.care.repository.CareSuggestionRepository;
import com.mxis.server.common.exception.BusinessException;
import com.mxis.server.common.exception.ErrorCode;
import com.mxis.server.device.entity.Device;
import com.mxis.server.product.entity.Product;
import com.mxis.server.product.entity.ProductDevice;
import com.mxis.server.product.repository.ProductDeviceRepository;
import com.mxis.server.product.repository.ProductRepository;
import com.mxis.server.sensor.dto.SensorAggregate;
import com.mxis.server.sensor.repository.SensorReadingRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CareQueryService {

    private static final int DRY_EXPOSURE_DAYS = 7;
    private static final int MONTHS_PER_YEAR = 12;

    private final ProductRepository productRepository;
    private final ProductDeviceRepository productDeviceRepository;
    private final CareReportRepository careReportRepository;
    private final CareSuggestionRepository careSuggestionRepository;
    private final SensorReadingRepository sensorReadingRepository;
    private final CareRuleEngine ruleEngine;

    public CareDashboardResponse getDashboard(Long userId, Long productId) {
        Product product = getOwnedProduct(userId, productId);
        CareReport report = latestReport(productId);

        Device primaryDevice = productDeviceRepository.findActivePrimaryByProductId(productId)
                .map(ProductDevice::getDevice)
                .orElse(null);

        CareSuggestion suggestion = careSuggestionRepository.findLatestActiveByProductId(productId).orElse(null);

        return new CareDashboardResponse(
                new CareDashboardResponse.ProductSummary(
                        product.getId(), product.getProductName(), product.getMaterial(),
                        product.getColor(), product.getImageUrl()),
                new CareDashboardResponse.DeviceSummary(
                        primaryDevice == null ? null : primaryDevice.getConnectionStatus(),
                        primaryDevice == null ? null : primaryDevice.getLastSyncedAt()),
                report.getConditionGrade(),
                report.getSummaryText(),
                report.getAnalysisText(),
                new CareDashboardResponse.EnvironmentSummary(
                        "최근 %d일 동안의 평균이에요".formatted(CareDiagnosisService.REPORT_PERIOD_DAYS),
                        new CareDashboardResponse.Measure(
                                report.getAvgTemperature(), ruleEngine.temperatureLabel(report.getAvgTemperature())),
                        new CareDashboardResponse.Measure(
                                report.getAvgHumidity(),
                                ruleEngine.humidityGrade(report.getAvgHumidity()).label()),
                        orZero(report.getOutingCount()),
                        ruleEngine.shockGrade(orZero(report.getShockCount())).label()),
                suggestion == null ? null : new CareDashboardResponse.ActiveSuggestion(
                        suggestion.getId(), suggestion.getMessage(), suggestion.getReasonText()));
    }

    /**
     * 저장된 30일 스냅샷에, 7일 건조노출·함께한시간을 조회 시점에 실시간 계산해 합쳐 반환한다.
     * (care_reports 스키마를 늘리지 않기 위한 의도적 선택)
     */
    public CareReportResponse getLatestReport(Long userId, Long productId) {
        Product product = getOwnedProduct(userId, productId);
        CareReport report = latestReport(productId);

        LocalDateTime now = LocalDateTime.now();
        SensorAggregate recent = sensorReadingRepository.aggregate(
                productId, now.minusDays(DRY_EXPOSURE_DAYS), now,
                CareRuleEngine.DRY_THRESHOLD, CareRuleEngine.STRONG_SHOCK_THRESHOLD);

        String period = "최근 %d일".formatted(CareDiagnosisService.REPORT_PERIOD_DAYS);

        return new CareReportResponse(
                report.getConditionGrade(),
                report.getSummaryText(),
                report.getAnalysisText(),
                new CareReportResponse.EnvironmentSummary(
                        new CareReportResponse.PeriodMeasure(
                                report.getAvgHumidity(), period,
                                ruleEngine.humidityGrade(report.getAvgHumidity()).label()),
                        new CareReportResponse.PeriodMeasure(
                                report.getAvgTemperature(), period,
                                ruleEngine.temperatureLabel(report.getAvgTemperature())),
                        new CareReportResponse.DryExposure(
                                "최근 %d일".formatted(DRY_EXPOSURE_DAYS),
                                ruleEngine.dryExposureLabel(recent.dryRatio()))),
                new CareReportResponse.UsagePattern(
                        timeTogether(product),
                        orZero(report.getOutingCount()),
                        orZero(report.getShockCount())),
                report.getRecommendationText(),
                report.getCreatedAt());
    }

    /** sensor_readings를 직접 집계하는 라이브 조회. care_reports를 거치지 않고 저장도 하지 않는다. */
    public SensorSummaryResponse getSensorSummary(Long userId, Long productId, SensorPeriod period) {
        getOwnedProduct(userId, productId);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = now.minusDays(period.days());
        LocalDateTime previousFrom = from.minusDays(period.days());

        SensorAggregate current = aggregate(productId, from, now);
        SensorAggregate previous = aggregate(productId, previousFrom, from);

        int outing = (int) sensorReadingRepository.countOutingSessions(productId, from, now);
        int shock = current.shockCountAsInt();
        boolean insufficientHistory = previous.isEmpty();

        return new SensorSummaryResponse(
                period,
                humidityTrend(productId, from, now),
                scale(current.avgTemperature()),
                scale(current.avgHumidity()),
                period.isYear() ? null : outing,
                period.isYear() ? null : shock,
                period.isYear() ? outing / MONTHS_PER_YEAR : null,
                period.isYear() ? shock / MONTHS_PER_YEAR : null,
                comparisonText(period, current, previous, insufficientHistory),
                insufficientHistory);
    }

    private SensorAggregate aggregate(Long productId, LocalDateTime from, LocalDateTime to) {
        return sensorReadingRepository.aggregate(
                productId, from, to, CareRuleEngine.DRY_THRESHOLD, CareRuleEngine.STRONG_SHOCK_THRESHOLD);
    }

    private List<SensorSummaryResponse.HumidityPoint> humidityTrend(
            Long productId, LocalDateTime from, LocalDateTime to) {
        return sensorReadingRepository.findDailyHumidity(productId, from, to).stream()
                .map(row -> new SensorSummaryResponse.HumidityPoint(
                        ((Date) row[0]).toLocalDate(),
                        BigDecimal.valueOf(((Number) row[1]).doubleValue()).setScale(1, RoundingMode.HALF_UP)))
                .toList();
    }

    /** 직전 동일 기간 대비 비교 문구. 규칙 기반 템플릿이며 AI를 쓰지 않는다. */
    private String comparisonText(SensorPeriod period, SensorAggregate current, SensorAggregate previous,
                                  boolean insufficientHistory) {
        if (insufficientHistory) {
            return period.isYear()
                    ? "MXIS와 함께한 지 아직 1년이 되지 않았습니다. 시간이 쌓일수록 계절의 흐름 속에서 변화해온 모습을 온전히 보여드릴 수 있습니다."
                    : "비교할 이전 기간의 데이터가 아직 충분하지 않습니다.";
        }
        if (current.avgHumidity() == null || previous.avgHumidity() == null) {
            return "이전 기간과 비교할 습도 데이터가 충분하지 않습니다.";
        }

        double delta = current.avgHumidity() - previous.avgHumidity();
        String humidityTrend = delta > 5 ? "습도가 다소 높아졌지만" : delta < -5 ? "습도가 다소 낮아졌지만" : "습도 변화는 크지 않았고";
        String shockTrend = current.shockCountAsInt() > previous.shockCountAsInt()
                ? "충격 감지는 이전보다 늘었습니다." : "충격 감지는 안정적인 수준이었습니다.";

        return "이전 기간보다 %s, %s".formatted(humidityTrend, shockTrend);
    }

    /** products.purchased_at(없으면 등록일) 기준 "N년 M개월". */
    private String timeTogether(Product product) {
        LocalDate since = product.getPurchasedAt() != null
                ? product.getPurchasedAt()
                : product.getRegisteredAt().toLocalDate();
        Period elapsed = Period.between(since, LocalDate.now());

        if (elapsed.isNegative()) {
            return "0개월";
        }
        if (elapsed.getYears() == 0) {
            return "%d개월".formatted(elapsed.getMonths());
        }
        return "%d년 %d개월".formatted(elapsed.getYears(), elapsed.getMonths());
    }

    private CareReport latestReport(Long productId) {
        return careReportRepository.findFirstByProductIdOrderByCreatedAtDesc(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_DIAGNOSIS_DATA));
    }

    private Product getOwnedProduct(Long userId, Long productId) {
        Product product = productRepository.findActiveById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        if (!product.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_OWNED);
        }
        return product;
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static BigDecimal scale(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }
}
