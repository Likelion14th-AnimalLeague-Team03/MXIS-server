package com.mxis.server.care.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mxis.server.care.dto.CareReportScreenResponse;
import com.mxis.server.care.entity.CareReport;
import com.mxis.server.care.repository.CareGuideRepository;
import com.mxis.server.care.repository.CareReportRepository;
import com.mxis.server.common.enums.CareConditionGrade;
import com.mxis.server.product.entity.Product;
import com.mxis.server.product.repository.ProductRepository;
import com.mxis.server.sensor.repository.SensorReadingRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CareScreenServiceTest {
    private final ProductRepository products = mock(ProductRepository.class);
    private final CareReportRepository reports = mock(CareReportRepository.class);
    private final CareDecisionPolicy policy = new CareDecisionPolicy(new CareRuleEngine());
    private final CareScreenService service = new CareScreenService(products, reports, mock(CareGuideRepository.class),
            mock(SensorReadingRepository.class), new CareRuleEngine(), mock(CareQueryService.class),
            new ObjectMapper(), policy, Clock.systemUTC());
    private CareReport report;

    @BeforeEach
    void setUp() {
        Product product = mock(Product.class);
        when(product.isOwnedBy(1L)).thenReturn(true);
        when(products.findActiveById(2L)).thenReturn(Optional.of(product));
        report = mock(CareReport.class);
        when(report.getPeriodEnd()).thenReturn(LocalDateTime.of(2026, 9, 14, 12, 0));
        when(reports.findFirstByProductIdOrderByCreatedAtDesc(2L)).thenReturn(Optional.of(report));
    }

    @Test
    void screenUsesPersistedMediumDecisionForCareAndVisitCycle() {
        when(report.getDataStatus()).thenReturn("SUFFICIENT");
        when(report.getCareNeed()).thenReturn("MEDIUM");
        when(report.getInspectionNeed()).thenReturn("NONE");
        when(report.getConditionGrade()).thenReturn(CareConditionGrade.BALANCED);
        // Legacy raw JSON cannot override the normalized decision.
        when(report.getAiOutput()).thenReturn("{\"aiCareSummary\":{\"careDecision\":{\"careNeed\":\"LOW\"}}}");
        CareReportScreenResponse response = service.getReport(1L, 2L);
        assertThat(response.careNeeded()).isTrue();
        assertThat(response.careCycleMonths()).isEqualTo(3);
        assertThat(response.nextCareRecommendedAt()).isEqualTo(report.getPeriodEnd().toLocalDate().plusMonths(3));
    }

    @Test
    void insufficientDataDoesNotInventCareVisitDate() {
        when(report.getDataStatus()).thenReturn("INSUFFICIENT_DATA");
        when(report.getConditionGrade()).thenReturn(CareConditionGrade.COLLECTING_DATA);
        CareReportScreenResponse response = service.getReport(1L, 2L);
        assertThat(response.careNeeded()).isFalse();
        assertThat(response.careCycleMonths()).isZero();
        assertThat(response.nextCareRecommendedAt()).isNull();
        assertThat(response.environment30d().humidityDescription()).isEqualTo("측정된 데이터가 없습니다");
    }
}
