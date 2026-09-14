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
import com.mxis.server.common.enums.CareType;
import com.mxis.server.common.exception.BusinessException;
import com.mxis.server.common.exception.ErrorCode;
import com.mxis.server.product.entity.Product;
import com.mxis.server.product.repository.ProductRepository;
import com.mxis.server.sensor.dto.SensorAggregate;
import com.mxis.server.sensor.repository.SensorReadingRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
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
    private final ObjectMapper objectMapper;
    private final CareDecisionPolicy decisionPolicy;
    private final Clock clock;

    public CareDiagnosisHomeResponse getDiagnosisHome(Long userId, Long productId) {
        Product product = getOwnedProduct(userId, productId);
        CareReport report = latestReport(productId);
        return new CareDiagnosisHomeResponse(
                ScreenProductSummary.from(product),
                sensorReadingRepository.countTotalOutingSessions(productId),
                diagnosisHomeCondition(report),
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
        boolean careNeeded = decisionPolicy.needsSuggestion(report.getDataStatus(), report.getCareNeed(),
                report.getInspectionNeed(), report.getConditionGrade());
        int careCycleMonths = decisionPolicy.careCycleMonths(report.getDataStatus(), report.getCareNeed(),
                report.getInspectionNeed(), report.getConditionGrade());
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
                careCycleMonths == 0 ? null : report.getPeriodEnd().toLocalDate().plusMonths(careCycleMonths));
    }

    public CareEnvironmentOverviewResponse getEnvironmentOverview(Long userId, Long productId) {
        getOwnedProduct(userId, productId);
        LocalDateTime now = LocalDateTime.now(clock);
        return new CareEnvironmentOverviewResponse(
                periodEnvironment(productId, SensorPeriod.SEVEN_DAYS, now),
                periodEnvironment(productId, SensorPeriod.THIRTY_DAYS, now),
                periodEnvironment(productId, SensorPeriod.ONE_YEAR, now));
    }

    public CareGuideResponse getGuide(Long userId, Long productId) {
        Product product = getOwnedProduct(userId, productId);
        CareReport report = latestReportOrNull(productId);
        JsonNode ai = report == null ? null : aiCareSummary(report);
        CareType careType = careType(ai, product, report);
        CareGuide guide = careGuideRepository.findFirstByCareTypeAndActiveTrue(careType.code())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "관리 가이드를 찾을 수 없습니다."));

        return new CareGuideResponse(
                product.getId(),
                product.getMaterialId(),
                product.getMaterialDisplayName(),
                careType.code(),
                guide.getGuideImageUrl(),
                guide.getTitle(),
                guide.getDescription(),
                guide.getSteps(),
                guide.getTip());
    }

    private CareEnvironmentOverviewResponse.PeriodEnvironment periodEnvironment(
            Long productId, SensorPeriod period, LocalDateTime now) {
        CareQueryService.EnvironmentSnapshot snapshot = careQueryService.environmentSnapshot(productId, period, now);
        CareEnvironmentResponse environment = snapshot.response();
        SensorAggregate aggregate = snapshot.aggregate();
        int outingCount = (int) sensorReadingRepository.countOutingSessions(
                productId, snapshot.window().from(), snapshot.window().to());
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

    private CareType careType(JsonNode ai, Product product, CareReport report) {
        CareType defaultType = "natural_leather".equals(product.getMaterialId())
                ? CareType.VENTILATED_SHADE_STORAGE : CareType.DRY_SOFT_CLOTH_WIPE;
        // Missing observations do not establish humidity, impact or long-term storage.
        if (report == null || !"SUFFICIENT".equals(report.getDataStatus())
                || report.getConditionGrade() == CareConditionGrade.COLLECTING_DATA) {
            return defaultType;
        }
        CareType explicit = CareType.fromCode(text(path(ai, "llmCopy", "careGuide", "careType")));
        if (explicit != null) return explicit;

        String primaryFactor = report.getPrimaryFactor();
        if (primaryFactor == null) primaryFactor = text(path(ai, "productCondition", "primaryFactor"));
        if (primaryFactor == null) return defaultType;
        return switch (primaryFactor) {
            // The Java fallback also uses humidity for low-humidity readings.
            case "humidity" -> report.getAvgHumidity() != null
                    && report.getAvgHumidity().compareTo(BigDecimal.valueOf(40)) < 0
                    ? CareType.AVOID_DRY_STORAGE : CareType.VENTILATED_HUMIDITY_DRY;
            case "dryness" -> CareType.AVOID_DRY_STORAGE;
            // The legacy fallback uses temperature_heat for both cold and hot readings.
            case "temperature_heat" -> report.getAvgTemperature() != null
                    && report.getAvgTemperature().compareTo(BigDecimal.valueOf(28)) > 0
                    ? CareType.AVOID_HEAT_COOL_DOWN : defaultType;
            case "handling" -> orZero(report.getShockCount()) > 0 ? CareType.SHOCK_IMPACT_CHECK : defaultType;
            // usage_rest alone cannot distinguish overuse from long-term storage.
            // Long-term storage is selected only by an explicit supported AI careType.
            default -> defaultType;
        };
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

    private CareDiagnosisHomeResponse.ConditionSummary diagnosisHomeCondition(CareReport report) {
        CareConditionGrade grade = "SUFFICIENT".equals(report.getDataStatus())
                && report.getConditionGrade() != null
                ? report.getConditionGrade() : CareConditionGrade.COLLECTING_DATA;
        return switch (grade) {
            case COLLECTING_DATA -> new CareDiagnosisHomeResponse.ConditionSummary(
                    "센서 데이터를 모으고 있습니다.", "최근 기록이 더 쌓이면 관리 상태를 알려드릴게요.");
            case STABLE -> new CareDiagnosisHomeResponse.ConditionSummary(
                    "안정적으로 유지되고 있습니다.", "최근 환경과 사용 기록이 권장 범위에 있습니다.");
            case BALANCED -> new CareDiagnosisHomeResponse.ConditionSummary(
                    "균형 있게 유지되고 있습니다.", "최근 환경과 사용 기록이 안정적인 범위에 있습니다.");
            case LIGHT_CARE -> new CareDiagnosisHomeResponse.ConditionSummary(
                    "가벼운 관리가 필요합니다.", "최근 기록에 맞춰 보관 환경과 사용 습관을 살펴봐주세요.");
            case EXPERT_CHECK -> new CareDiagnosisHomeResponse.ConditionSummary(
                    "전문가의 확인을 권장합니다.", "최근 환경과 사용 기록에서 점검이 필요한 신호가 있습니다.");
        };
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

    private Product getOwnedProduct(Long userId, Long productId) {
        Product product = productRepository.findActiveById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        if (!product.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_OWNED);
        }
        return product;
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


    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static BigDecimal scale(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }
}
