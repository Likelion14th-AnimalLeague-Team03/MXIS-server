package com.mxis.server.care.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mxis.server.care.dto.AiCareSummaryResponse;
import com.mxis.server.care.dto.CareDiagnosisHomeResponse;
import com.mxis.server.care.dto.CareEnvironmentOverviewResponse;
import com.mxis.server.care.dto.CareEnvironmentResponse;
import com.mxis.server.care.dto.CareGuideResponse;
import com.mxis.server.care.dto.CareReportScreenResponse;
import com.mxis.server.care.dto.ScreenProductSummary;
import com.mxis.server.care.dto.SensorPeriod;
import com.mxis.server.care.service.CareQueryService;
import com.mxis.server.care.service.CareScreenService;
import com.mxis.server.common.security.JwtAuthenticationFilter;
import com.mxis.server.common.security.JwtTokenProvider;
import com.mxis.server.config.SecurityConfig;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CareBaseController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class CareBaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CareQueryService careQueryService;

    @MockBean
    private CareScreenService careScreenService;

    private String accessToken;

    @BeforeEach
    void setUp() {
        accessToken = jwtTokenProvider.createAccessToken(1L, "user@mxis.com");
    }

    @Test
    void getAiCareSummary_success_returnsAiContractFields() throws Exception {
        given(careQueryService.getAiCareSummary(eq(1L), eq(20L), eq(SensorPeriod.SEVEN_DAYS)))
                .willReturn(new AiCareSummaryResponse(
                        20L,
                        LocalDateTime.now(),
                        7,
                        new AiCareSummaryResponse.DataSufficiency(
                                "SUFFICIENT", null, 144, 24.0, LocalDateTime.now(), LocalDateTime.now()),
                        new AiCareSummaryResponse.ProductCondition(
                                "Standard", 76, "humidity", "최근 습도가 안정 범위를 벗어난 시간이 있습니다."),
                        new AiCareSummaryResponse.StressLabels(
                                "CAUTION", "LOW", "LOW", "LOW", "LOW", "UNKNOWN"),
                        new AiCareSummaryResponse.Explanation(
                                "최근 고습 노출이 일부 누적되어 예방 관리가 권장됩니다.",
                                List.of("습도 누적 노출이 확인되었습니다."),
                                List.of("MVP 센서는 UV/light를 직접 측정하지 않습니다.")),
                        new AiCareSummaryResponse.CopyGeneration("deterministic_fallback", null, null)));

        mockMvc.perform(get("/api/v1/care/products/20/summary")
                        .param("period", "7D")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productCondition.label", is("Standard")))
                .andExpect(jsonPath("$.data.stressLabels.uvLight", is("UNKNOWN")))
                .andExpect(jsonPath("$.data.explanation.short", is("최근 고습 노출이 일부 누적되어 예방 관리가 권장됩니다.")));
    }

    @Test
    void getCareEnvironment_thirtyDays_returnsThreeDayAveragePoints() throws Exception {
        given(careQueryService.getCareEnvironment(eq(1L), eq(20L), eq(SensorPeriod.THIRTY_DAYS)))
                .willReturn(new CareEnvironmentResponse(
                        20L,
                        SensorPeriod.THIRTY_DAYS,
                        LocalDateTime.now(),
                        new AiCareSummaryResponse.DataSufficiency(
                                "SUFFICIENT", null, 300, 720.0, LocalDateTime.now(), LocalDateTime.now()),
                        new CareEnvironmentResponse.EnvironmentSummary(
                                new BigDecimal("24.8"), new BigDecimal("68.1"),
                                "CAUTION", "LOW", "LOW", "LOW", "UNKNOWN"),
                        List.of(
                                new CareEnvironmentResponse.EnvironmentPoint(
                                        "2026-08-01~2026-08-03",
                                        LocalDate.of(2026, 8, 1),
                                        LocalDate.of(2026, 8, 3),
                                        new BigDecimal("24.8"),
                                        new BigDecimal("68.1"),
                                        30)),
                        new CareEnvironmentResponse.EnvironmentCopy(
                                "그래프의 순간값보다 누적 시간이 중요합니다.",
                                List.of("30D는 3일 평균 10개로 구성됩니다."))));

        mockMvc.perform(get("/api/v1/care/products/20/environment")
                        .param("period", "30D")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period", is("30D")))
                .andExpect(jsonPath("$.data.points[0].avgHumidity", is(68.1)))
                .andExpect(jsonPath("$.data.copy.short", is("그래프의 순간값보다 누적 시간이 중요합니다.")));
    }

    @Test
    void getCareEnvironment_invalidPeriod_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/care/products/20/environment")
                        .param("period", "3M")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_INPUT")));
    }

    @Test
    void getDiagnosisHome_success_returnsScreenFields() throws Exception {
        given(careScreenService.getDiagnosisHome(eq(1L), eq(20L))).willReturn(new CareDiagnosisHomeResponse(
                sampleProduct(),
                45,
                new CareDiagnosisHomeResponse.ConditionSummary(
                        "균형 있게 유지되고 있습니다.",
                        "최근 환경과 사용 기록이 안정적인 범위에 있습니다."),
                new CareDiagnosisHomeResponse.Environment30d(
                        new BigDecimal("24.1"),
                        "이상적입니다.",
                        new BigDecimal("48.2"),
                        "이상적입니다.",
                        "낮음",
                        18)));

        mockMvc.perform(get("/api/v1/care/products/20/diagnosis-home")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.product.materialDisplayName", is("Visetos Canvas")))
                .andExpect(jsonPath("$.data.totalOutingCount", is(45)))
                .andExpect(jsonPath("$.data.condition.summary", is("균형 있게 유지되고 있습니다.")))
                .andExpect(jsonPath("$.data.environment30d.outingCount", is(18)));
    }

    @Test
    void getReport_success_returnsCareNeedAndCycleFields() throws Exception {
        given(careScreenService.getReport(eq(1L), eq(20L))).willReturn(new CareReportScreenResponse(
                100L,
                LocalDateTime.of(2026, 8, 17, 12, 0),
                new CareReportScreenResponse.ConditionReport(
                        "안정적인 상태입니다.",
                        "최근 환경과 사용 기록 정보를 기반으로 관리 상태를 안내합니다."),
                new CareReportScreenResponse.Environment30d(
                        new BigDecimal("24.1"),
                        "이상적입니다.",
                        new BigDecimal("48.2"),
                        "이상적입니다.",
                        "낮음",
                        18),
                "다음 계절 전 가벼운 점검을 권장합니다.",
                false,
                6,
                LocalDate.of(2027, 2, 17)));

        mockMvc.perform(get("/api/v1/care/products/20/report")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.careReportId", is(100)))
                .andExpect(jsonPath("$.data.condition.summary", is("안정적인 상태입니다.")))
                .andExpect(jsonPath("$.data.careNeeded", is(false)))
                .andExpect(jsonPath("$.data.careCycleMonths", is(6)));
    }

    @Test
    void getEnvironmentOverview_success_returnsAllPeriods() throws Exception {
        given(careScreenService.getEnvironmentOverview(eq(1L), eq(20L))).willReturn(new CareEnvironmentOverviewResponse(
                samplePeriod("7D", 7),
                samplePeriod("30D", 18),
                samplePeriod("1Y", 92)));

        mockMvc.perform(get("/api/v1/care/products/20/environment/overview")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sevenDays.period", is("7D")))
                .andExpect(jsonPath("$.data.thirtyDays.outingCount", is(18)))
                .andExpect(jsonPath("$.data.oneYear.temperaturePoints[0].label", is("2026-08")));
    }

    @Test
    void getGuide_success_returnsGuideImageAndSteps() throws Exception {
        given(careScreenService.getGuide(eq(1L), eq(20L))).willReturn(new CareGuideResponse(
                20L,
                "canvas",
                "Visetos Canvas",
                "dry_soft_cloth_wipe",
                "http://161.33.38.65:8080/images/canvas.png",
                "이번 주에는 마른 부드러운 천으로 표면을 정돈해주세요",
                "먼지와 오염을 부드럽게 제거하여 가죽의 컨디션을 유지해 주세요.",
                List.of("마른 부드러운 천을 준비해주세요.", "결 방향을 따라 부드럽게 닦아주세요."),
                "정기적으로 관리하면 가죽의 광택과 수명을 오래 유지할 수 있어요."));

        mockMvc.perform(get("/api/v1/care/products/20/guide")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.careType", is("dry_soft_cloth_wipe")))
                .andExpect(jsonPath("$.data.guideImageUrl", is("http://161.33.38.65:8080/images/canvas.png")))
                .andExpect(jsonPath("$.data.title", is("이번 주에는 마른 부드러운 천으로 표면을 정돈해주세요")))
                .andExpect(jsonPath("$.data.materialDisplayName", is("Visetos Canvas")))
                .andExpect(jsonPath("$.data.steps[0]", is("마른 부드러운 천을 준비해주세요.")));
    }

    private ScreenProductSummary sampleProduct() {
        return new ScreenProductSummary(
                20L,
                "https://example.com/product.png",
                "Stark Backpack",
                "canvas",
                "Visetos Canvas",
                "Cognac",
                "MMKAAVE01CO001",
                null);
    }

    private CareEnvironmentOverviewResponse.PeriodEnvironment samplePeriod(String period, int outingCount) {
        return new CareEnvironmentOverviewResponse.PeriodEnvironment(
                period,
                List.of(new CareEnvironmentOverviewResponse.MetricPoint("2026-08", new BigDecimal("24.1"))),
                List.of(new CareEnvironmentOverviewResponse.MetricPoint("2026-08", new BigDecimal("48.2"))),
                new BigDecimal("24.1"),
                new BigDecimal("48.2"),
                outingCount,
                2,
                "온·습도 환경은 안정적이었습니다.");
    }
}
