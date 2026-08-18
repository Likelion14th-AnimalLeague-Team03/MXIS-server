package com.mxis.server.care.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private static final String CARE_TYPE_VENTILATED_SHADE_STORAGE = "ventilated_shade_storage";
    private static final String CARE_TYPE_DRY_SOFT_CLOTH_WIPE = "dry_soft_cloth_wipe";

    private final ProductRepository productRepository;
    private final CareReportRepository careReportRepository;
    private final CareGuideRepository careGuideRepository;
    private final SensorReadingRepository sensorReadingRepository;
    private final CareRuleEngine ruleEngine;
    private final CareQueryService careQueryService;
    private final ObjectMapper objectMapper;

    public CareDiagnosisHomeResponse getDiagnosisHome(Long userId, Long productId) {
        Product product = getOwnedProduct(userId, productId);
        CareReport report = latestReport(productId);
        JsonNode ai = aiCareSummary(report);
        return new CareDiagnosisHomeResponse(
                ScreenProductSummary.from(product),
                sensorReadingRepository.countTotalOutingSessions(productId),
                new CareDiagnosisHomeResponse.ConditionSummary(
                        diagnosisHomeSummary(ai, report),
                        diagnosisHomeDescription(ai, report)),
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
        JsonNode ai = aiCareSummary(report);
        boolean careNeeded = careNeeded(ai, report);
        int careCycleMonths = careCycleMonths(ai, report.getConditionGrade());
        return new CareReportScreenResponse(
                report.getId(),
                report.getCreatedAt(),
                new CareReportScreenResponse.ConditionReport(
                        careReportSummary(ai, report),
                        careReportDetail(ai, report)),
                new CareReportScreenResponse.Environment30d(
                        report.getAvgTemperature(),
                        ruleEngine.temperatureLabel(report.getAvgTemperature()),
                        report.getAvgHumidity(),
                        ruleEngine.humidityGrade(report.getAvgHumidity()).label(),
                        ruleEngine.shockGrade(orZero(report.getShockCount())).label(),
                        orZero(report.getOutingCount())),
                interpretation(ai, report),
                careNeeded,
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
        CareReport report = latestReportOrNull(productId);
        JsonNode ai = report == null ? null : aiCareSummary(report);
        String careType = careType(ai, product);
        CareGuide guide = findGuide(careType, product);
        JsonNode aiCareGuide = path(ai, "llmCopy", "careGuide");

        return new CareGuideResponse(
                product.getId(),
                product.getMaterialId(),
                product.getMaterialDisplayName(),
                careType,
                guide.getGuideImageUrl(),
                guide.getTitle(),
                firstText(path(aiCareGuide, "description"), textNode(guide.getDescription()), null),
                firstList(path(aiCareGuide, "steps"), guide.getSteps()),
                firstText(path(aiCareGuide, "tip"), path(aiCareGuide, "weeklyTip"), textNode(guide.getTip())));
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

    private CareGuide findGuide(String careType, Product product) {
        CareGuide typeGuide = careGuideRepository.findFirstByCareTypeAndActiveTrue(careType).orElse(null);
        if (typeGuide != null) {
            return typeGuide;
        }

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

    private String careType(JsonNode ai, Product product) {
        String explicitCareType = text(path(ai, "llmCopy", "careGuide", "careType"));
        if (CARE_TYPE_VENTILATED_SHADE_STORAGE.equals(explicitCareType)
                || CARE_TYPE_DRY_SOFT_CLOTH_WIPE.equals(explicitCareType)) {
            return explicitCareType;
        }

        String primaryFactor = text(path(ai, "productCondition", "primaryFactor"));
        if ("temperature_heat".equals(primaryFactor)
                || "humidity".equals(primaryFactor)
                || "dryness".equals(primaryFactor)) {
            return CARE_TYPE_VENTILATED_SHADE_STORAGE;
        }
        if ("natural_leather".equals(product.getMaterialId())) {
            return CARE_TYPE_VENTILATED_SHADE_STORAGE;
        }
        return CARE_TYPE_DRY_SOFT_CLOTH_WIPE;
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

    private CareReport latestReportOrNull(Long productId) {
        return careReportRepository.findFirstByProductIdOrderByCreatedAtDesc(productId).orElse(null);
    }

    private JsonNode aiCareSummary(CareReport report) {
        String aiOutput = report.getAiOutput();
        if (aiOutput == null || aiOutput.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(aiOutput);
            JsonNode summary = root.path("aiCareSummary");
            return summary.isMissingNode() ? null : summary;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String diagnosisHomeSummary(JsonNode ai, CareReport report) {
        return firstText(
                path(ai, "llmCopy", "diagnosisHome", "short"),
                path(ai, "explanation", "short"),
                textNode(report.getSummaryText()));
    }

    private String diagnosisHomeDescription(JsonNode ai, CareReport report) {
        List<String> bullets = stringList(path(ai, "llmCopy", "diagnosisHome", "reasonBullets"));
        if (bullets.isEmpty()) {
            bullets = stringList(path(ai, "explanation", "reasonBullets"));
        }
        if (!bullets.isEmpty()) {
            return String.join(" ", bullets);
        }
        return report.getAnalysisText();
    }

    private String careReportSummary(JsonNode ai, CareReport report) {
        return firstText(
                path(ai, "llmCopy", "careReport", "short"),
                path(ai, "explanation", "short"),
                textNode(report.getSummaryText()));
    }

    private String careReportDetail(JsonNode ai, CareReport report) {
        List<String> bullets = stringList(path(ai, "llmCopy", "careReport", "reasonBullets"));
        if (bullets.isEmpty()) {
            bullets = stringList(path(ai, "explanation", "reasonBullets"));
        }
        if (!bullets.isEmpty()) {
            return String.join(" ", bullets);
        }
        return report.getAnalysisText();
    }

    private String interpretation(JsonNode ai, CareReport report) {
        return firstText(
                path(ai, "llmCopy", "environmentDetail", "short"),
                path(ai, "llmCopy", "careGuide", "weeklyTip"),
                textNode(report.getRecommendationText()));
    }

    private boolean careNeeded(JsonNode ai, CareReport report) {
        String careNeed = text(path(ai, "careDecision", "careNeed"));
        String inspectionNeed = text(path(ai, "careDecision", "inspectionNeed"));
        if (careNeed != null || inspectionNeed != null) {
            return "REQUIRED".equals(inspectionNeed)
                    || "CONDITIONAL".equals(inspectionNeed)
                    || "MEDIUM".equals(careNeed)
                    || "MEDIUM_HIGH".equals(careNeed)
                    || "HIGH".equals(careNeed);
        }
        return ruleEngine.needsSuggestion(report.getConditionGrade());
    }

    private Product getOwnedProduct(Long userId, Long productId) {
        Product product = productRepository.findActiveById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        if (!product.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_OWNED);
        }
        return product;
    }

    private int careCycleMonths(JsonNode ai, CareConditionGrade grade) {
        String careNeed = text(path(ai, "careDecision", "careNeed"));
        String inspectionNeed = text(path(ai, "careDecision", "inspectionNeed"));
        if ("REQUIRED".equals(inspectionNeed) || "HIGH".equals(careNeed)) {
            return 1;
        }
        if ("CONDITIONAL".equals(inspectionNeed) || "MEDIUM_HIGH".equals(careNeed) || "MEDIUM".equals(careNeed)) {
            return 3;
        }
        if ("LOW_MEDIUM".equals(careNeed)) {
            return 6;
        }
        return careCycleMonths(grade);
    }

    private int careCycleMonths(CareConditionGrade grade) {
        return switch (grade) {
            case STABLE, BALANCED -> 6;
            case LIGHT_CARE -> 3;
            case EXPERT_CHECK -> 1;
        };
    }

    private JsonNode path(JsonNode node, String... fieldNames) {
        JsonNode current = node;
        for (String fieldName : fieldNames) {
            if (current == null || current.isMissingNode() || current.isNull()) {
                return null;
            }
            current = current.path(fieldName);
        }
        return current == null || current.isMissingNode() || current.isNull() ? null : current;
    }

    private String text(JsonNode node) {
        return node != null && node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
    }

    private JsonNode textNode(String value) {
        return value == null ? null : objectMapper.getNodeFactory().textNode(value);
    }

    private String firstText(JsonNode first, JsonNode second, JsonNode third) {
        JsonNode[] nodes = {first, second, third};
        for (JsonNode node : nodes) {
            String text = text(node);
            if (text != null) {
                return text;
            }
        }
        return "";
    }

    private List<String> stringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(node.spliterator(), false)
                .filter(JsonNode::isTextual)
                .map(JsonNode::asText)
                .toList();
    }

    private List<String> firstList(JsonNode node, List<String> fallback) {
        List<String> values = stringList(node);
        if (!values.isEmpty()) {
            return values;
        }
        return fallback == null ? List.of() : fallback;
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static BigDecimal scale(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }
}
