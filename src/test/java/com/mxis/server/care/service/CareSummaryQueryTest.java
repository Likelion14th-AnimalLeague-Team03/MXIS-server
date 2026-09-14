package com.mxis.server.care.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mxis.server.care.dto.SensorPeriod;
import com.mxis.server.care.entity.CareReport;
import com.mxis.server.care.repository.CareReportRepository;
import com.mxis.server.product.entity.Product;
import com.mxis.server.product.repository.ProductRepository;
import com.mxis.server.sensor.dto.SensorAggregate;
import com.mxis.server.sensor.repository.SensorReadingRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CareSummaryQueryTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-14T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final LocalDateTime now = LocalDateTime.now(clock);
    private final ProductRepository products = mock(ProductRepository.class);
    private final SensorReadingRepository sensors = mock(SensorReadingRepository.class);
    private final CareReportRepository reports = mock(CareReportRepository.class);
    private final CareDiagnosisJobService jobs = mock(CareDiagnosisJobService.class);
    private final CareRuleEngine rules = new CareRuleEngine();
    private final CareQueryService service = new CareQueryService(products, null, reports, null, sensors,
            rules, new CareDecisionPolicy(rules), jobs, new AiResponseMapper(new ObjectMapper()), clock);

    @BeforeEach void setUp() {
        Product product = mock(Product.class);
        when(products.findActiveById(7L)).thenReturn(Optional.of(product));
        when(product.isOwnedBy(1L)).thenReturn(true);
        when(sensors.findReadingStats(eq(7L), any(), any())).thenReturn(new Object[]{24L, now.minusHours(25), now.minusMinutes(1), now.minusMinutes(1), 11L});
        when(sensors.aggregate(eq(7L), any(), any(), any(), any())).thenReturn(
                new SensorAggregate(20.0, BigDecimal.valueOf(20), BigDecimal.valueOf(20), 75.0, 24L, 0L, 0L));
    }

    @Test void freshSnapshotReturnsActualAiScoreWithoutRegenerationOrRawReadings() {
        CareReport report = mock(CareReport.class);
        when(report.getSensorRevision()).thenReturn(11L);
        when(report.getPeriodEnd()).thenReturn(now.minusMinutes(1));
        when(report.getAiOutput()).thenReturn(AiResponseMapperTest.validJson());
        when(reports.findFirstByProductIdAndAnalysisWindowDaysOrderByPeriodEndDescIdDesc(7L, 30)).thenReturn(Optional.of(report));
        var response = service.getAiCareSummary(1L, 7L, SensorPeriod.THIRTY_DAYS);
        assertThat(response.productCondition().score()).isEqualTo(92);
        verifyNoInteractions(jobs);
        verify(sensors, never()).aggregate(any(), any(), any(), any(), any());
        verify(sensors, never()).findByProductIdAndMeasuredAtGreaterThanEqualAndMeasuredAtLessThanOrderByMeasuredAtAsc(any(), any(), any());
    }

    @Test void rawInputCountMetadataLetsFilteredAiResultReuseTheSnapshot() {
        CareReport report = mock(CareReport.class);
        when(report.getSensorRevision()).thenReturn(11L);
        when(report.getPeriodEnd()).thenReturn(now.minusMinutes(1));
        when(sensors.findReadingStats(eq(7L), any(), any())).thenReturn(new Object[]{24L, now.minusHours(25), now.minusMinutes(1), now.minusMinutes(1), 11L, 30L});
        String raw = AiResponseMapperTest.validJson().replace("{\"aiCareSummary\":", "{\"backendSnapshot\":{\"readingCount\":30},\"aiCareSummary\":");
        when(report.getAiOutput()).thenReturn(raw);
        when(reports.findFirstByProductIdAndAnalysisWindowDaysOrderByPeriodEndDescIdDesc(7L, 30)).thenReturn(Optional.of(report));
        assertThat(service.getAiCareSummary(1L, 7L, SensorPeriod.THIRTY_DAYS).productCondition().score()).isEqualTo(92);
        verifyNoInteractions(jobs);
    }

    @Test void missingSnapshotQueuesRequestedPeriodAndUsesCanonicalFallback() {
        var response = service.getAiCareSummary(1L, 7L, SensorPeriod.SEVEN_DAYS);
        assertThat(response.analysisWindowDays()).isEqualTo(7);
        assertThat(response.productCondition().score()).isEqualTo(50);
        assertThat(response.productCondition().label()).isEqualTo("Needs Attention");
        verify(jobs).request(7L, SensorPeriod.SEVEN_DAYS, now);
    }

    @Test void newSensorRevisionDoesNotReuseOldNormalResult() {
        CareReport report = mock(CareReport.class);
        when(report.getSensorRevision()).thenReturn(10L);
        when(reports.findFirstByProductIdAndAnalysisWindowDaysOrderByPeriodEndDescIdDesc(7L, 30)).thenReturn(Optional.of(report));
        assertThat(service.getAiCareSummary(1L, 7L, SensorPeriod.THIRTY_DAYS).productCondition().score()).isEqualTo(50);
        verify(jobs).request(7L, SensorPeriod.THIRTY_DAYS, now);
    }

    @Test void noDataDoesNotReturnNormalScore() {
        when(sensors.findReadingStats(eq(7L), any(), any())).thenReturn(new Object[]{0L, null, null, null, null});
        var response = service.getAiCareSummary(1L, 7L, SensorPeriod.THIRTY_DAYS);
        assertThat(response.dataSufficiency().status()).isEqualTo("NO_DATA");
        assertThat(response.productCondition().score()).isNull();
    }
}
