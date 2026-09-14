package com.mxis.server.care.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
import com.mxis.server.common.enums.CareSuggestionStatus;
import com.mxis.server.device.entity.Device;
import com.mxis.server.notification.service.NotificationService;
import com.mxis.server.product.entity.Product;
import com.mxis.server.product.repository.ProductRepository;
import com.mxis.server.sensor.dto.SensorAggregate;
import com.mxis.server.sensor.entity.SensorReading;
import com.mxis.server.sensor.repository.SensorReadingRepository;
import com.mxis.server.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class CareDiagnosisServiceTest {
    private final CareAlgorithmRepository algorithms = mock(CareAlgorithmRepository.class);
    private final CareReportRepository reports = mock(CareReportRepository.class);
    private final CareSuggestionRepository suggestions = mock(CareSuggestionRepository.class);
    private final SensorReadingRepository sensors = mock(SensorReadingRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final MxisAiClient aiClient = mock(MxisAiClient.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final CareDecisionPolicy policy = new CareDecisionPolicy(new CareRuleEngine());
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 14, 12, 0);
    private final Clock clock = Clock.fixed(now.atZone(ZoneId.of("Asia/Seoul")).toInstant(), ZoneId.of("Asia/Seoul"));
    private final CareDiagnosisService service = new CareDiagnosisService(algorithms, reports, suggestions,
            sensors, products, new CareRuleEngine(), policy, notifications, aiClient, mapper,
            new TestTransactionManager(), entityManager, clock);
    private Product product;
    private CareAlgorithm algorithm;
    private final SensorAggregate stats = new SensorAggregate(20.0, BigDecimal.valueOf(20), BigDecimal.valueOf(20), 50.0, 24L, 0L, 0L);

    @BeforeEach
    void setUp() {
        User user = User.createLocal("a@mxis.com", "encoded", "사용자", "01000000000");
        ReflectionTestUtils.setField(user, "id", 2L);
        product = new Product(user, null, "가방", null, "natural_leather", "가죽", List.of(), null, null, null, null);
        ReflectionTestUtils.setField(product, "id", 1L);
        algorithm = mock(CareAlgorithm.class);
        when(algorithm.getId()).thenReturn(3L);
        when(products.findActiveById(1L)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
            return Optional.of(product);
        });
        when(algorithms.findByIsActiveTrue()).thenReturn(Optional.of(algorithm));
        when(sensors.aggregate(eq(1L), any(), any(), any(), any())).thenReturn(stats);
        when(sensors.findReadingStats(eq(1L), any(), any())).thenReturn(new Object[] {24L, now.minusDays(2), now.minusHours(1), now, 42L});
        when(entityManager.find(Product.class, 1L, LockModeType.PESSIMISTIC_WRITE)).thenReturn(product);
        when(entityManager.getReference(CareAlgorithm.class, 3L)).thenReturn(algorithm);
        when(reports.findFirstByProductIdAndAnalysisWindowDaysOrderByPeriodEndDescIdDesc(anyLong(), anyInt())).thenReturn(Optional.empty());
        when(reports.save(any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
            return invocation.getArgument(0);
        });
        when(suggestions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(suggestions.findAllActiveIncludingExpiredByProductId(1L)).thenReturn(List.of());
    }

    @Test
    void aiRunsOutsideTransactionAndStoredScoreAndMediumSuggestionArePreserved() throws Exception {
        enableAi(result("SUFFICIENT", 87, "Standard", "MEDIUM"));
        service.regenerate(1L);
        CareReport report = savedReport();
        assertThat(report.getConditionScore()).isEqualTo(87);
        assertThat(report.getDataStatus()).isEqualTo("SUFFICIENT");
        assertThat(report.getAnalysisWindowDays()).isEqualTo(30);
        assertThat(report.getPeriodStart()).isEqualTo(now.minusDays(30));
        assertThat(report.getSensorRevision()).isEqualTo(42L);
        assertThat(report.getConditionGrade()).isEqualTo(CareConditionGrade.LIGHT_CARE);
        assertThat(mapper.readTree(report.getAiOutput()).path("backendSnapshot").path("readingCount").asLong()).isEqualTo(24L);
        verify(suggestions).save(any(CareSuggestion.class));
        var order = inOrder(entityManager);
        order.verify(entityManager).find(User.class, 2L, LockModeType.PESSIMISTIC_WRITE);
        order.verify(entityManager).find(Product.class, 1L, LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void aiInsufficientResponseExpiresOldWarningAndDoesNotStoreNormalScore() throws Exception {
        enableAi(result("INSUFFICIENT_DATA", null, "Collecting Data", "UNKNOWN"));
        CareSuggestion old = new CareSuggestion(null, product, "경고", null, null, null, null, now.plusDays(1));
        when(suggestions.findAllActiveIncludingExpiredByProductId(1L)).thenReturn(List.of(old));
        service.regenerate(1L);
        assertThat(savedReport().getConditionGrade()).isEqualTo(CareConditionGrade.COLLECTING_DATA);
        assertThat(savedReport().getConditionScore()).isNull();
        assertThat(old.getStatus()).isEqualTo(CareSuggestionStatus.EXPIRED);
        verify(suggestions, never()).save(any());
    }

    @Test
    void normalRecoveryAlsoExpiresPreviousSuggestion() {
        CareSuggestion old = new CareSuggestion(null, product, "경고", null, null, null, null, now.plusDays(1));
        when(suggestions.findAllActiveIncludingExpiredByProductId(1L)).thenReturn(List.of(old));
        service.regenerate(1L);
        assertThat(savedReport().getConditionGrade()).isEqualTo(CareConditionGrade.STABLE);
        assertThat(old.getStatus()).isEqualTo(CareSuggestionStatus.EXPIRED);
        verify(suggestions, never()).save(any());
    }

    @Test
    void noDataStillPersistsCollectingSnapshotWithoutExternalCall() {
        when(aiClient.isEnabled()).thenReturn(true);
        when(sensors.aggregate(eq(1L), any(), any(), any(), any())).thenReturn(new SensorAggregate(null, null, null, null, 0L, 0L, 0L));
        when(sensors.findReadingStats(eq(1L), any(), any())).thenReturn(new Object[] {0L, null, null, null, null});
        service.regenerate(1L);
        assertThat(savedReport().getDataStatus()).isEqualTo("NO_DATA");
        assertThat(savedReport().getConditionScore()).isNull();
        verify(aiClient, never()).getCareSummaryResult(any(), any(), any(), any());
    }

    @Test
    void shorterWindowSnapshotCannotReplaceThirtyDaySuggestions() {
        service.regenerate(1L, SensorPeriod.SEVEN_DAYS, now);
        assertThat(savedReport().getAnalysisWindowDays()).isEqualTo(7);
        verify(suggestions, never()).findAllActiveIncludingExpiredByProductId(anyLong());
    }

    @Test
    void olderConcurrentResultCannotOverwriteNewerSnapshot() {
        CareReport newer = mock(CareReport.class);
        when(newer.getPeriodEnd()).thenReturn(now.plusMinutes(1));
        when(reports.findFirstByProductIdAndAnalysisWindowDaysOrderByPeriodEndDescIdDesc(1L, 30)).thenReturn(Optional.of(newer));
        service.regenerate(1L);
        verify(reports, never()).save(any());
        verify(suggestions, never()).findAllActiveIncludingExpiredByProductId(anyLong());
    }

    @Test
    void failedAiPersistsSafeFallbackButSignalsDurableWorkerToRetry() throws Exception {
        enableAi(result("SUFFICIENT", 92, "Excellent", "LOW"));
        AiServiceException failure = new AiServiceException(AiServiceException.Category.TIMEOUT, "Timeout");
        when(aiClient.getCareSummaryResult(eq(product), eq(4L), any(), any())).thenThrow(failure);
        assertThatThrownBy(() -> service.regenerate(1L)).isSameAs(failure);
        CareReport report = savedReport();
        assertThat(mapper.readTree(report.getAiOutput()).path("backendSource").asText()).isEqualTo("JAVA_RULES");
        assertThat(new AiResponseMapper(mapper).read(1L, SensorPeriod.THIRTY_DAYS, report.getAiOutput()).summary().productCondition().score())
                .isEqualTo(100);
    }

    @Test
    void retryCanReplaceJavaFallbackWithSuccessfulPythonRuleResult() throws Exception {
        CareReport previous = mock(CareReport.class);
        when(previous.getPeriodEnd()).thenReturn(now);
        when(previous.getSensorRevision()).thenReturn(42L);
        when(previous.getAiOutput()).thenReturn("{\"backendSource\":\"JAVA_RULES\"}");
        when(reports.findFirstByProductIdAndAnalysisWindowDaysOrderByPeriodEndDescIdDesc(1L, 30)).thenReturn(Optional.of(previous));
        MxisAiClient.CareSummaryResult remote = result("SUFFICIENT", 91, "Excellent", "LOW");
        ((ObjectNode) remote.aiCareSummary().path("copyGeneration")).put("source", "deterministic_fallback");
        enableAi(remote);
        service.regenerate(1L);
        assertThat(savedReport().getConditionScore()).isEqualTo(91);
    }

    @Test
    void duplicateSuccessfulResultCannotCreateAnotherReportOrNotification() throws Exception {
        CareReport previous = mock(CareReport.class);
        when(previous.getPeriodEnd()).thenReturn(now);
        when(previous.getSensorRevision()).thenReturn(42L);
        when(previous.getAiOutput()).thenReturn("{\"aiCareSummary\":{}}");
        when(reports.findFirstByProductIdAndAnalysisWindowDaysOrderByPeriodEndDescIdDesc(1L, 30)).thenReturn(Optional.of(previous));
        enableAi(result("SUFFICIENT", 75, "Standard", "MEDIUM"));
        service.regenerate(1L);
        verify(reports, never()).save(any());
        verifyNoInteractions(notifications);
    }

    @Test
    void newerWindowCanExpireOldHighestRevisionReading() {
        CareReport previous = mock(CareReport.class);
        when(previous.getPeriodEnd()).thenReturn(now.minusDays(1));
        when(previous.getSensorRevision()).thenReturn(99L);
        when(reports.findFirstByProductIdAndAnalysisWindowDaysOrderByPeriodEndDescIdDesc(1L, 30)).thenReturn(Optional.of(previous));
        service.regenerate(1L);
        assertThat(savedReport().getSensorRevision()).isEqualTo(42L);
    }

    @Test
    void unchangedCareDecisionPreservesSuggestionIdAndOriginalVisitWindow() throws Exception {
        CareReport oldReport = mock(CareReport.class);
        when(oldReport.getConditionGrade()).thenReturn(CareConditionGrade.LIGHT_CARE);
        when(oldReport.getCareNeed()).thenReturn("MEDIUM");
        when(oldReport.getInspectionNeed()).thenReturn("NONE");
        CareSuggestion old = new CareSuggestion(oldReport, product, "경고", null, null,
                now.toLocalDate().plusDays(4), now.toLocalDate().plusMonths(1), now.plusDays(1));
        ReflectionTestUtils.setField(old, "id", 99L);
        when(suggestions.findAllActiveIncludingExpiredByProductId(1L)).thenReturn(List.of(old));
        enableAi(result("SUFFICIENT", 75, "Standard", "MEDIUM"));
        service.regenerate(1L);
        assertThat(old.getStatus()).isEqualTo(CareSuggestionStatus.ACTIVE);
        assertThat(old.getId()).isEqualTo(99L);
        assertThat(old.getRecommendedVisitFrom()).isEqualTo(now.toLocalDate().plusDays(4));
        assertThat(old.getRecommendedVisitTo()).isEqualTo(now.toLocalDate().plusMonths(1));
        assertThat(old.getExpiresAt()).isEqualTo(now.plusDays(1));
        verify(suggestions, never()).save(any());
        verifyNoInteractions(notifications);
    }

    @Test
    void changedCarePolicyReplacesSuggestionEvenWhenGradeIsUnchanged() throws Exception {
        CareReport oldReport = mock(CareReport.class);
        when(oldReport.getConditionGrade()).thenReturn(CareConditionGrade.LIGHT_CARE);
        when(oldReport.getCareNeed()).thenReturn("MEDIUM");
        when(oldReport.getInspectionNeed()).thenReturn("NONE");
        CareSuggestion old = new CareSuggestion(oldReport, product, "경고", null, null, null, null, now.plusDays(1));
        when(suggestions.findAllActiveIncludingExpiredByProductId(1L)).thenReturn(List.of(old));
        enableAi(result("SUFFICIENT", 60, "Standard", "MEDIUM_HIGH"));
        service.regenerate(1L);
        assertThat(old.getStatus()).isEqualTo(CareSuggestionStatus.EXPIRED);
        verify(suggestions).save(any());
        verify(notifications).createCareTimingNotificationIfNeeded(any());
    }

    @Test
    void snapshotCacheMetadataKeepsRawCountSeparateFromValidReadings() throws Exception {
        when(sensors.findReadingStats(eq(1L), any(), any()))
                .thenReturn(new Object[] {0L, null, null, now, 42L, 50L});
        service.regenerate(1L);
        CareReport report = savedReport();
        assertThat(report.getDataStatus()).isEqualTo("NO_DATA");
        var root = mapper.readTree(report.getAiOutput());
        assertThat(root.path("aiCareSummary").path("dataSufficiency").path("validReadingCount").asLong()).isZero();
        assertThat(root.path("backendSnapshot").path("readingCount").asLong()).isEqualTo(50L);
    }

    private void enableAi(MxisAiClient.CareSummaryResult result) {
        when(aiClient.isEnabled()).thenReturn(true);
        SensorReading reading = mock(SensorReading.class);
        Device device = mock(Device.class);
        when(reading.getDevice()).thenReturn(device);
        when(device.getId()).thenReturn(4L);
        when(sensors.findByProductIdAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(eq(1L), any(), any()))
                .thenReturn(List.of(reading));
        when(aiClient.getCareSummaryResult(eq(product), eq(4L), any(), any())).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return result;
        });
    }

    private MxisAiClient.CareSummaryResult result(String status, Integer score, String label, String careNeed) throws Exception {
        AiCareSummaryResponse base = policy.fallback(1L, SensorPeriod.THIRTY_DAYS, now, stats,
                new AiCareSummaryResponse.DataSufficiency(status, null, 24, 47.0, now.minusHours(1), now));
        AiCareSummaryResponse summary = new AiCareSummaryResponse(1L, now, 30, base.dataSufficiency(),
                new AiCareSummaryResponse.ProductCondition(label, score, null, "AI 요약"), base.stressLabels(),
                base.explanation(), new AiCareSummaryResponse.CopyGeneration("ai", null, null));
        ObjectNode node = mapper.valueToTree(summary);
        node.putObject("careDecision").put("careNeed", careNeed).put("inspectionNeed", "NONE");
        ObjectNode root = mapper.createObjectNode();
        root.set("aiCareSummary", node);
        return new MxisAiClient.CareSummaryResult(summary, mapper.writeValueAsString(root), root, node);
    }

    private CareReport savedReport() {
        ArgumentCaptor<CareReport> captor = ArgumentCaptor.forClass(CareReport.class);
        verify(reports).save(captor.capture());
        return captor.getValue();
    }

    /** Exercises TransactionTemplate's real synchronization boundaries without an external database. */
    private static class TestTransactionManager extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { }
        @Override protected void doRollback(DefaultTransactionStatus status) { }
    }
}
