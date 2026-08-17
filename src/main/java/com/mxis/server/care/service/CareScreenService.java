package com.mxis.server.care.service;

import com.mxis.server.care.dto.CareDiagnosisHomeResponse;
import com.mxis.server.care.dto.CareEnvironmentOverviewResponse;
import com.mxis.server.care.dto.CareEnvironmentResponse;
import com.mxis.server.care.dto.CareGuideResponse;
import com.mxis.server.care.dto.CareReportScreenResponse;
import com.mxis.server.care.dto.ScreenProductSummary;
import com.mxis.server.care.dto.SensorPeriod;
import com.mxis.server.care.entity.CareGuide;
import com.mxis.server.care.entity.CareReport;
import com.mxis.server.care.repository.CareGuideRepository;
import com.mxis.server.care.repository.CareReportRepository;
import com.mxis.server.common.enums.CareConditionGrade;
import com.mxis.server.common.exception.BusinessException;
import com.mxis.server.common.exception.ErrorCode;
import com.mxis.server.product.entity.Product;
import com.mxis.server.product.repository.ProductRepository;
import com.mxis.server.sensor.dto.SensorAggregate;
import com.mxis.server.sensor.repository.SensorReadingRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CareScreenService {

    private final ProductRepository productRepository;
    private final CareReportRepository careReportRepository;
    private final CareGuideRepository careGuideRepository;
    private final SensorReadingRepository sensorReadingRepository;
    private final CareRuleEngine ruleEngine;
    private final CareQueryService careQueryService;

    public CareDiagnosisHomeResponse getDiagnosisHome(Long userId, Long productId) {
        Product product = getOwnedProduct(userId, productId);
        CareReport report = latestReport(productId);
        return new CareDiagnosisHomeResponse(
                ScreenProductSummary.from(product),
                sensorReadingRepository.countTotalOutingSessions(productId),
                new CareDiagnosisHomeResponse.ConditionSummary(
                        report.getSummaryText(),
                        report.getAnalysisText()),
                new CareDiagnosisHomeResponse.Environment30d(
                        report.getAvgTemperature(),
                        ruleEngine.temperatureLabel(report.getAvgTemperature()),
                        report.getAvgHumidity(),
                        ruleEngine.humidityGrade(report.getAvgHumidity()).label(),
                        ruleEngine.shockGrade(orZero(report.getShockCount())).label(),
                        orZero(report.getOutingCount())));
    }

    public CareReportScreenResponse getReport(Long userId, Long productId) {
        getOwnedProduct(userId, productId);
        CareReport report = latestReport(productId);
        int careCycleMonths = careCycleMonths(report.getConditionGrade());
        return new CareReportScreenResponse(
                report.getId(),
                report.getCreatedAt(),
                new CareReportScreenResponse.ConditionReport(
                        report.getSummaryText(),
                        report.getAnalysisText()),
                new CareReportScreenResponse.Environment30d(
                        report.getAvgTemperature(),
                        ruleEngine.temperatureLabel(report.getAvgTemperature()),
                        report.getAvgHumidity(),
                        ruleEngine.humidityGrade(report.getAvgHumidity()).label(),
                        ruleEngine.shockGrade(orZero(report.getShockCount())).label(),
                        orZero(report.getOutingCount())),
                report.getRecommendationText(),
                ruleEngine.needsSuggestion(report.getConditionGrade()),
                careCycleMonths,
                report.getPeriodEnd().toLocalDate().plusMonths(careCycleMonths));
    }

    public CareEnvironmentOverviewResponse getEnvironmentOverview(Long userId, Long productId) {
        getOwnedProduct(userId, productId);
        return new CareEnvironmentOverviewResponse(
                periodEnvironment(userId, productId, SensorPeriod.SEVEN_DAYS),
                periodEnvironment(userId, productId, SensorPeriod.THIRTY_DAYS),
                periodEnvironment(userId, productId, SensorPeriod.ONE_YEAR));
    }

    public CareGuideResponse getGuide(Long userId, Long productId) {
        Product product = getOwnedProduct(userId, productId);
        CareGuide guide = findGuide(product);
        return new CareGuideResponse(
                product.getId(),
                product.getMaterialId(),
                product.getMaterialDisplayName(),
                guide.getGuideImageUrl(),
                guide.getTitle(),
                guide.getDescription(),
                guide.getSteps(),
                guide.getTip());
    }

    private CareEnvironmentOverviewResponse.PeriodEnvironment periodEnvironment(
            Long userId, Long productId, SensorPeriod period) {
        CareEnvironmentResponse environment = careQueryService.getCareEnvironment(userId, productId, period);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = period == SensorPeriod.ONE_YEAR ? now.minusYears(1) : now.minusDays(period.days());
        SensorAggregate aggregate = sensorReadingRepository.aggregate(
                productId, from, now,
                CareRuleEngine.DRY_THRESHOLD, CareRuleEngine.STRONG_SHOCK_THRESHOLD);
        int outingCount = (int) sensorReadingRepository.countOutingSessions(productId, from, now);
        int shockCount = aggregate.shockCountAsInt();

        return new CareEnvironmentOverviewResponse.PeriodEnvironment(
                period.code(),
                environment.points().stream()
                        .map(point -> new CareEnvironmentOverviewResponse.MetricPoint(
                                point.label(), point.avgTemperature()))
                        .toList(),
                environment.points().stream()
                        .map(point -> new CareEnvironmentOverviewResponse.MetricPoint(
                                point.label(), point.avgHumidity()))
                        .toList(),
                scale(aggregate.avgTemperature()),
                scale(aggregate.avgHumidity()),
                outingCount,
                shockCount,
                interpretation(period, aggregate, outingCount, shockCount));
    }

    private CareGuide findGuide(Product product) {
        List<String> subtypes = product.getMaterialSubtypes() == null ? List.of() : product.getMaterialSubtypes();
        for (String subtype : subtypes) {
            CareGuide subtypeGuide = careGuideRepository
                    .findFirstByMaterialIdAndMaterialSubtypeAndActiveTrue(product.getMaterialId(), subtype)
                    .orElse(null);
            if (subtypeGuide != null) {
                return subtypeGuide;
            }
        }
        return careGuideRepository.findFirstByMaterialIdAndMaterialSubtypeIsNullAndActiveTrue(product.getMaterialId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "관리 가이드를 찾을 수 없습니다."));
    }

    private String interpretation(SensorPeriod period, SensorAggregate aggregate, int outingCount, int shockCount) {
        if (aggregate.isEmpty()) {
            return "아직 해당 기간의 환경 데이터가 충분하지 않습니다.";
        }
        String range = switch (period) {
            case SEVEN_DAYS -> "최근 7일";
            case THIRTY_DAYS -> "최근 30일";
            case ONE_YEAR -> "최근 1년";
        };
        String humidity = ruleEngine.humidityGrade(scale(aggregate.avgHumidity())).label();
        String temperature = ruleEngine.temperatureLabel(scale(aggregate.avgTemperature()));
        String shock = ruleEngine.shockGrade(shockCount).label();
        return "%s 동안 온도는 %s, 습도는 %s 수준이었고 외출 %d회, 충격 정도는 %s으로 기록되었습니다."
                .formatted(range, temperature, humidity, outingCount, shock);
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

    private int careCycleMonths(CareConditionGrade grade) {
        return switch (grade) {
            case STABLE, BALANCED -> 6;
            case LIGHT_CARE -> 3;
            case EXPERT_CHECK -> 1;
        };
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static BigDecimal scale(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }
}
