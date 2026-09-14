package com.mxis.server.care.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mxis.server.care.dto.AiCareSummaryResponse;
import com.mxis.server.care.dto.SensorPeriod;
import com.mxis.server.care.entity.CareAlgorithm;
import com.mxis.server.care.entity.CareReport;
import com.mxis.server.care.entity.CareSuggestion;
import com.mxis.server.care.repository.CareAlgorithmRepository;
import com.mxis.server.care.repository.CareReportRepository;
import com.mxis.server.care.repository.CareSuggestionRepository;
import com.mxis.server.common.enums.CareConditionGrade;
import com.mxis.server.notification.service.NotificationService;
import com.mxis.server.product.entity.Product;
import com.mxis.server.user.entity.User;
import com.mxis.server.product.repository.ProductRepository;
import com.mxis.server.sensor.dto.SensorAggregate;
import com.mxis.server.sensor.entity.SensorReading;
import com.mxis.server.sensor.repository.SensorReadingRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Reads a committed sensor snapshot, calls AI outside a transaction, then atomically saves the result. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class CareDiagnosisService {
    static final int REPORT_PERIOD_DAYS = 30;
    private final CareAlgorithmRepository careAlgorithmRepository;
    private final CareReportRepository careReportRepository;
    private final CareSuggestionRepository careSuggestionRepository;
    private final SensorReadingRepository sensorReadingRepository;
    private final ProductRepository productRepository;
    private final CareRuleEngine ruleEngine;
    private final CareDecisionPolicy decisionPolicy;
    private final NotificationService notificationService;
    private final MxisAiClient mxisAiClient;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;
    private final EntityManager entityManager;
    private final Clock clock;

    public void regenerate(Product product) {
        regenerate(product.getId());
    }

    public void regenerate(Long productId) {
        regenerate(productId, SensorPeriod.THIRTY_DAYS, LocalDateTime.now(clock));
    }

    public void regenerate(Long productId, SensorPeriod period, LocalDateTime periodEnd) {
        LocalDateTime snapshotEnd = periodEnd.truncatedTo(ChronoUnit.MICROS);
        Snapshot snapshot = transaction(true).execute(status -> readSnapshot(productId, period, snapshotEnd));
        if (snapshot == null) return;
        MxisAiClient.CareSummaryResult result = null;
        AiServiceException failure = null;
        if (mxisAiClient.isEnabled() && "SUFFICIENT".equals(snapshot.sufficiency().status())) {
            try {
                result = mxisAiClient.getCareSummaryResult(snapshot.product(), snapshot.deviceId(), period, snapshot.readings());
            } catch (AiServiceException ex) {
                failure = ex;
                log.warn("AI diagnosis unavailable; saving rule fallback. productId={}, period={}", productId, period);
            }
        }
        if (result == null) result = fallback(snapshot, period);
        MxisAiClient.CareSummaryResult completed = withSnapshotMetadata(snapshot, result);
        transaction(false).executeWithoutResult(status -> persist(snapshot, completed));
        // Durable job retries can replace this fallback when the external service recovers.
        if (failure != null) throw failure;
    }

    private TransactionTemplate transaction(boolean readOnly) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(readOnly);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private Snapshot readSnapshot(Long productId, SensorPeriod period, LocalDateTime periodEnd) {
        Product product = productRepository.findActiveById(productId).orElse(null);
        if (product == null) return null;
        CareAlgorithm algorithm = careAlgorithmRepository.findByIsActiveTrue()
                .orElseThrow(() -> new IllegalStateException("No active care algorithm"));
        LocalDateTime periodStart = periodEnd.minusDays(period.days());
        SensorAggregate stats = sensorReadingRepository.aggregate(productId, periodStart, periodEnd,
                CareRuleEngine.DRY_THRESHOLD, CareRuleEngine.STRONG_SHOCK_THRESHOLD);
        Object[] row = sensorReadingRepository.findReadingStats(productId, periodStart, periodEnd);
        if (row.length == 1 && row[0] instanceof Object[] nested) row = nested;
        long count = row[0] == null ? 0 : ((Number) row[0]).longValue();
        AiCareSummaryResponse.DataSufficiency sufficiency = decisionPolicy.dataSufficiency(count,
                dateTime(row[1]), dateTime(row[2]), dateTime(row[3]), periodEnd);
        long revision = row.length < 5 || row[4] == null ? 0 : ((Number) row[4]).longValue();
        long inputReadingCount = row.length < 6 || row[5] == null ? count : ((Number) row[5]).longValue();
        List<SensorReading> readings = mxisAiClient.isEnabled() && "SUFFICIENT".equals(sufficiency.status())
                ? sensorReadingRepository.findByProductIdAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(
                        productId, periodStart, periodEnd) : List.of();
        Long deviceId = readings.isEmpty() ? null : readings.get(readings.size() - 1).getDevice().getId();
        return new Snapshot(product, product.getUser().getId(), algorithm.getId(), periodStart, periodEnd, stats,
                (int) sensorReadingRepository.countOutingSessions(productId, periodStart, periodEnd),
                sufficiency, revision, inputReadingCount, readings, deviceId);
    }

    private MxisAiClient.CareSummaryResult fallback(Snapshot snapshot, SensorPeriod period) {
        AiCareSummaryResponse summary = decisionPolicy.fallback(snapshot.product().getId(), period,
                snapshot.periodEnd(), snapshot.stats(), snapshot.sufficiency());
        CareConditionGrade grade = decisionPolicy.grade(summary, null, null);
        // The worst combined exposure has a stricter grade than the public three-label scale.
        if ("SUFFICIENT".equals(summary.dataSufficiency().status())
                && ruleEngine.conditionGrade(ruleEngine.humidityGrade(scale(snapshot.stats().avgHumidity())),
                ruleEngine.shockGrade(snapshot.stats().shockCountAsInt())) == CareConditionGrade.EXPERT_CHECK) {
            grade = CareConditionGrade.EXPERT_CHECK;
        }
        ObjectNode ai = objectMapper.valueToTree(summary);
        ai.putObject("careDecision")
                .put("careNeed", decisionPolicy.fallbackCareNeed(grade))
                .put("inspectionNeed", grade == CareConditionGrade.EXPERT_CHECK ? "REQUIRED" : "NONE");
        ObjectNode root = objectMapper.createObjectNode().put("schemaVersion", "care-report-snapshot-v1")
                .put("backendSource", "JAVA_RULES");
        root.set("aiCareSummary", ai);
        try {
            return new MxisAiClient.CareSummaryResult(summary, objectMapper.writeValueAsString(root), root, ai);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize care snapshot", ex);
        }
    }

    private MxisAiClient.CareSummaryResult withSnapshotMetadata(Snapshot snapshot, MxisAiClient.CareSummaryResult result) {
        ObjectNode root = result.root().deepCopy();
        root.putObject("backendSnapshot")
                .put("readingCount", snapshot.inputReadingCount())
                .put("sensorRevision", snapshot.revision())
                .put("periodStart", snapshot.periodStart().toString())
                .put("periodEnd", snapshot.periodEnd().toString());
        try {
            return new MxisAiClient.CareSummaryResult(result.summary(), objectMapper.writeValueAsString(root),
                    root, root.path("aiCareSummary"));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize care snapshot metadata", ex);
        }
    }

    private void persist(Snapshot snapshot, MxisAiClient.CareSummaryResult result) {
        // Match ingestion and device mutation lock order: owner, then product.
        entityManager.find(User.class, snapshot.ownerId(), LockModeType.PESSIMISTIC_WRITE);
        Product product = entityManager.find(Product.class, snapshot.product().getId(), LockModeType.PESSIMISTIC_WRITE);
        if (product == null || product.isDeleted()) return;
        CareReport previous = careReportRepository.findFirstByProductIdAndAnalysisWindowDaysOrderByPeriodEndDescIdDesc(
                product.getId(), result.summary().analysisWindowDays()).orElse(null);
        if (previous != null && (previous.getPeriodEnd().isAfter(snapshot.periodEnd())
                || previous.getPeriodEnd().equals(snapshot.periodEnd()) && previous.getSensorRevision() != null
                && previous.getSensorRevision() > snapshot.revision())) {
            return;
        }
        if (previous != null && previous.getPeriodEnd().equals(snapshot.periodEnd())
                && previous.getSensorRevision() != null && previous.getSensorRevision() == snapshot.revision()) {
            boolean nextIsFallback = "JAVA_RULES".equals(result.root().path("backendSource").asText());
            boolean previousIsFallback = isFallback(previous.getAiOutput());
            if (nextIsFallback || !previousIsFallback) return;
        }
        AiCareSummaryResponse summary = result.summary();
        JsonNode ai = result.aiCareSummary();
        String careNeed = ai.path("careDecision").path("careNeed").asText(null);
        String inspectionNeed = ai.path("careDecision").path("inspectionNeed").asText(null);
        CareConditionGrade grade = decisionPolicy.grade(summary, careNeed, inspectionNeed);
        CareAlgorithm algorithm = entityManager.getReference(CareAlgorithm.class, snapshot.algorithmId());
        SensorAggregate stats = snapshot.stats();
        CareReport report = careReportRepository.save(new CareReport(product, algorithm, grade,
                summaryText(ai), analysisText(ai), recommendationText(ai), snapshot.periodStart(), snapshot.periodEnd(),
                scale(stats.avgTemperature()), stats.maxTemperature(), stats.minTemperature(), scale(stats.avgHumidity()),
                snapshot.outingCount(), stats.shockCountAsInt(), result.rawJson(), summary, careNeed, inspectionNeed, snapshot.revision()));
        if (summary.analysisWindowDays() != REPORT_PERIOD_DAYS) return;
        List<CareSuggestion> previousSuggestions = careSuggestionRepository.findAllActiveIncludingExpiredByProductId(product.getId());
        if (!decisionPolicy.needsSuggestion(report.getDataStatus(), careNeed, inspectionNeed, grade)) {
            // Recovery and insufficient-data results invalidate the previous warning.
            previousSuggestions.forEach(CareSuggestion::expire);
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        CareSuggestion equivalent = previousSuggestions.stream()
                .filter(suggestion -> suggestion.getExpiresAt() == null || suggestion.getExpiresAt().isAfter(now))
                .filter(suggestion -> equivalentDecision(suggestion.getCareReport(), report))
                .findFirst().orElse(null);
        // An unchanged refresh keeps the user's existing suggestion ID, visit window and expiry.
        previousSuggestions.stream().filter(suggestion -> suggestion != equivalent).forEach(CareSuggestion::expire);
        if (equivalent == null) createSuggestion(product, report, grade);
    }

    private boolean equivalentDecision(CareReport previous, CareReport current) {
        return previous != null && previous.getConditionGrade() == current.getConditionGrade()
                && Objects.equals(previous.getCareNeed(), current.getCareNeed())
                && Objects.equals(previous.getInspectionNeed(), current.getInspectionNeed());
    }

    private boolean isFallback(String rawJson) {
        if (rawJson == null) return false;
        try {
            return "JAVA_RULES".equals(objectMapper.readTree(rawJson).path("backendSource").asText());
        } catch (JsonProcessingException ignored) {
            return false;
        }
    }

    private void createSuggestion(Product product, CareReport report, CareConditionGrade grade) {
        LocalDate visitFrom = LocalDate.now(clock).plusDays(4);
        LocalDate visitTo = visitFrom.plusMonths(1);
        CareSuggestion suggestion = careSuggestionRepository.save(new CareSuggestion(report, product,
                ruleEngine.suggestionMessage(grade), ruleEngine.suggestionReason(grade), ruleEngine.recommendedService(grade),
                visitFrom, visitTo, visitTo.atTime(23, 59, 59)));
        notificationService.createCareTimingNotificationIfNeeded(suggestion);
    }

    private LocalDateTime dateTime(Object value) {
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        return value instanceof LocalDateTime local ? local : null;
    }

    private record Snapshot(Product product, Long ownerId, Long algorithmId, LocalDateTime periodStart, LocalDateTime periodEnd,
            SensorAggregate stats, int outingCount, AiCareSummaryResponse.DataSufficiency sufficiency,
            long revision, long inputReadingCount, List<SensorReading> readings, Long deviceId) { }

    private String summaryText(JsonNode aiCareSummary) {
        return firstText(
                aiCareSummary.path("llmCopy").path("diagnosisHome").path("short"),
                aiCareSummary.path("explanation").path("short"),
                aiCareSummary.path("productCondition").path("summary"),
                "제품 상태 분석 결과입니다.");
    }

    private String analysisText(JsonNode aiCareSummary) {
        String shortText = firstText(
                aiCareSummary.path("llmCopy").path("careReport").path("short"),
                aiCareSummary.path("explanation").path("short"),
                null);
        List<String> bullets = stringList(firstArray(
                aiCareSummary.path("llmCopy").path("careReport").path("reasonBullets"),
                aiCareSummary.path("explanation").path("reasonBullets")));
        if (shortText == null && bullets.isEmpty()) {
            return "AI 분석 결과를 기반으로 최근 관리 상태를 안내합니다.";
        }
        if (bullets.isEmpty()) {
            return shortText;
        }
        return shortText == null ? String.join(" ", bullets) : shortText + " " + String.join(" ", bullets);
    }

    private String recommendationText(JsonNode aiCareSummary) {
        return firstText(
                aiCareSummary.path("llmCopy").path("careGuide").path("weeklyTip"),
                aiCareSummary.path("reservationCta").path("description"),
                aiCareSummary.path("explanation").path("short"),
                "현재 상태에 맞는 관리 습관을 유지해 주세요.");
    }

    private JsonNode firstArray(JsonNode first, JsonNode second) {
        if (first != null && first.isArray()) {
            return first;
        }
        return second;
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

    private String firstText(JsonNode first, JsonNode second, String fallback) {
        return firstText(first, second, null, fallback);
    }

    private String firstText(JsonNode first, JsonNode second, JsonNode third, String fallback) {
        JsonNode[] nodes = {first, second, third};
        for (JsonNode node : nodes) {
            if (node != null && node.isTextual() && !node.asText().isBlank()) {
                return node.asText();
            }
        }
        return fallback;
    }

    private static BigDecimal scale(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
