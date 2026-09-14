package com.mxis.server.care.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mxis.server.care.dto.CareReportScreenResponse;
import com.mxis.server.care.entity.CareReport;
import com.mxis.server.care.entity.CareGuide;
import com.mxis.server.common.enums.CareType;
import com.mxis.server.common.exception.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
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
    private final CareGuideRepository guides = mock(CareGuideRepository.class);
    private final CareDecisionPolicy policy = new CareDecisionPolicy(new CareRuleEngine());
    private final CareScreenService service = new CareScreenService(products, reports, guides,
            mock(SensorReadingRepository.class), new CareRuleEngine(), mock(CareQueryService.class),
            new ObjectMapper(), policy, Clock.systemUTC());
    private CareReport report;
    private Product product;

    @BeforeEach
    void setUp() {
        product = mock(Product.class);
        when(product.getMaterialId()).thenReturn("natural_leather");
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

    @ParameterizedTest
    @CsvSource({
            "STABLE,안정적으로 유지되고 있습니다.,최근 환경과 사용 기록이 권장 범위에 있습니다.",
            "BALANCED,균형 있게 유지되고 있습니다.,최근 환경과 사용 기록이 안정적인 범위에 있습니다.",
            "LIGHT_CARE,가벼운 관리가 필요합니다.,최근 기록에 맞춰 보관 환경과 사용 습관을 살펴봐주세요.",
            "EXPERT_CHECK,전문가의 확인을 권장합니다.,최근 환경과 사용 기록에서 점검이 필요한 신호가 있습니다.",
            "COLLECTING_DATA,센서 데이터를 모으고 있습니다.,최근 기록이 더 쌓이면 관리 상태를 알려드릴게요."
    })
    void diagnosisUsesTwoShortSentencesInsteadOfLongAiCopy(CareConditionGrade grade, String title, String description) {
        when(report.getDataStatus()).thenReturn("SUFFICIENT");
        when(report.getConditionGrade()).thenReturn(grade);
        when(report.getAiOutput()).thenReturn("""
                {"aiCareSummary":{"llmCopy":{"diagnosisHome":{"short":"긴 AI 문장", "reasonBullets":["첫 설명", "둘째 설명"]}}}}
                """);
        var condition = service.getDiagnosisHome(1L, 2L).condition();
        assertThat(condition.summary()).isEqualTo(title);
        assertThat(condition.description()).isEqualTo(description);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NO_DATA", "INSUFFICIENT_DATA", "STALE_DATA"})
    void insufficientStatusCannotShowHealthyCopy(String status) {
        when(report.getDataStatus()).thenReturn(status);
        when(report.getConditionGrade()).thenReturn(CareConditionGrade.STABLE);
        var condition = service.getDiagnosisHome(1L, 2L).condition();
        assertThat(condition.summary()).isEqualTo("센서 데이터를 모으고 있습니다.");
    }

    @ParameterizedTest
    @EnumSource(CareType.class)
    void allSevenExplicitTypesReturnOneCoherentCatalogEntry(CareType type) {
        sufficientReport();
        when(report.getAiOutput()).thenReturn("""
                {"aiCareSummary":{"llmCopy":{"careGuide":{"careType":"%s",
                "description":"unrelated AI description","steps":["wrong action"],"tip":"wrong tip"}}}}
                """.formatted(type.code()));
        catalog(type);
        var result = service.getGuide(1L, 2L);
        assertThat(result.careType()).isEqualTo(type.code());
        assertThat(result.guideImageUrl()).isEqualTo("http://161.33.38.65:8080/images/" + type.code() + ".png");
        assertThat(result.title()).isEqualTo(type.code() + " title");
        assertThat(result.description()).isEqualTo(type.code() + " description");
        assertThat(result.steps()).containsExactly(type.code() + " step");
        assertThat(result.tip()).isEqualTo(type.code() + " tip");
    }

    @ParameterizedTest
    @CsvSource({
            "humidity,75,22,0,VENTILATED_HUMIDITY_DRY",
            "humidity,30,22,0,AVOID_DRY_STORAGE",
            "dryness,40,22,0,AVOID_DRY_STORAGE",
            "temperature_heat,45,32,0,AVOID_HEAT_COOL_DOWN",
            "temperature_heat,45,10,0,VENTILATED_SHADE_STORAGE",
            "handling,45,22,4,SHOCK_IMPACT_CHECK",
            "handling,45,22,0,VENTILATED_SHADE_STORAGE",
            "usage_rest,45,22,0,VENTILATED_SHADE_STORAGE",
            "unknown,45,22,0,VENTILATED_SHADE_STORAGE"
    })
    void unsupportedAiTypeUsesRecordedFactorWithoutInventingStorageOrHeat(
            String factor, int humidity, int temperature, int shocks, CareType expected) {
        sufficientReport();
        when(report.getPrimaryFactor()).thenReturn(factor);
        when(report.getAvgHumidity()).thenReturn(BigDecimal.valueOf(humidity));
        when(report.getAvgTemperature()).thenReturn(BigDecimal.valueOf(temperature));
        when(report.getShockCount()).thenReturn(shocks);
        when(report.getAiOutput()).thenReturn("""
                {"aiCareSummary":{"llmCopy":{"careGuide":{"careType":"unsupported_type"}}}}
                """);
        catalog(expected);
        assertThat(service.getGuide(1L, 2L).careType()).isEqualTo(expected.code());
    }

    @Test
    void noReportUsesGeneralMaterialDefault() {
        when(reports.findFirstByProductIdOrderByCreatedAtDesc(2L)).thenReturn(Optional.empty());
        when(product.getMaterialId()).thenReturn("canvas");
        catalog(CareType.DRY_SOFT_CLOTH_WIPE);
        assertThat(service.getGuide(1L, 2L).careType()).isEqualTo("dry_soft_cloth_wipe");
    }

    @ParameterizedTest
    @ValueSource(strings = {"NO_DATA", "INSUFFICIENT_DATA", "STALE_DATA"})
    void missingObservationsAreNotLongTermStorageEvidence(String status) {
        when(report.getDataStatus()).thenReturn(status);
        when(report.getAiOutput()).thenReturn("""
                {"aiCareSummary":{"llmCopy":{"careGuide":{"careType":"long_term_storage_check"}}}}
                """);
        catalog(CareType.VENTILATED_SHADE_STORAGE);
        assertThat(service.getGuide(1L, 2L).careType()).isEqualTo("ventilated_shade_storage");
    }

    @Test
    void missingCatalogEntryDoesNotMixAnotherTypesImageAndContent() {
        sufficientReport();
        when(report.getPrimaryFactor()).thenReturn("dryness");
        assertThatThrownBy(() -> service.getGuide(1L, 2L)).isInstanceOf(BusinessException.class);
        verify(guides).findFirstByCareTypeAndActiveTrue("avoid_dry_storage");
        verifyNoMoreInteractions(guides);
    }

    private void sufficientReport() {
        when(report.getDataStatus()).thenReturn("SUFFICIENT");
        when(report.getConditionGrade()).thenReturn(CareConditionGrade.BALANCED);
    }

    private void catalog(CareType type) {
        CareGuide guide = mock(CareGuide.class);
        when(guide.getGuideImageUrl()).thenReturn("http://161.33.38.65:8080/images/" + type.code() + ".png");
        when(guide.getTitle()).thenReturn(type.code() + " title");
        when(guide.getDescription()).thenReturn(type.code() + " description");
        when(guide.getSteps()).thenReturn(List.of(type.code() + " step"));
        when(guide.getTip()).thenReturn(type.code() + " tip");
        when(guides.findFirstByCareTypeAndActiveTrue(type.code())).thenReturn(Optional.of(guide));
    }
}
