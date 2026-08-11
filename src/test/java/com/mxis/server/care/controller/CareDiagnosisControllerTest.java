package com.mxis.server.care.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mxis.server.care.dto.CareDashboardResponse;
import com.mxis.server.care.dto.CareReportResponse;
import com.mxis.server.care.dto.CareSuggestionResponse;
import com.mxis.server.care.dto.SensorPeriod;
import com.mxis.server.care.dto.SensorSummaryResponse;
import com.mxis.server.care.service.CareQueryService;
import com.mxis.server.care.service.CareSuggestionService;
import com.mxis.server.common.enums.CareConditionGrade;
import com.mxis.server.common.enums.CareSuggestionStatus;
import com.mxis.server.common.enums.DeviceConnectionStatus;
import com.mxis.server.common.exception.BusinessException;
import com.mxis.server.common.exception.ErrorCode;
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

@WebMvcTest(CareDiagnosisController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class CareDiagnosisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CareQueryService careQueryService;

    @MockBean
    private CareSuggestionService careSuggestionService;

    private String accessToken;

    @BeforeEach
    void setUp() {
        accessToken = jwtTokenProvider.createAccessToken(1L, "user@mxis.com");
    }

    @Test
    void getCareDashboard_requiresAuth_returns401WithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/products/20/care-dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCareDashboard_success_returnsGradeAndLabels() throws Exception {
        given(careQueryService.getDashboard(eq(1L), eq(20L))).willReturn(new CareDashboardResponse(
                new CareDashboardResponse.ProductSummary(20L, "MCM Aren Shopper", "Visetos Canvas", "Cognac", null),
                new CareDashboardResponse.DeviceSummary(DeviceConnectionStatus.CONNECTED, LocalDateTime.now()),
                CareConditionGrade.BALANCED,
                "균형 있게 유지되고 있습니다.",
                "최근 환경과 사용 기록이 안정적인 범위에 있습니다.",
                new CareDashboardResponse.EnvironmentSummary(
                        "최근 30일 동안의 평균이에요",
                        new CareDashboardResponse.Measure(new BigDecimal("22.00"), "이상적입니다"),
                        new CareDashboardResponse.Measure(new BigDecimal("42.00"), "이상적입니다"),
                        18, "낮음"),
                new CareDashboardResponse.ActiveSuggestion(5L, "가벼운 점검을 제안드려요.", "누적 기록 기반")));

        mockMvc.perform(get("/api/v1/products/20/care-dashboard")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conditionGrade", is("BALANCED")))
                .andExpect(jsonPath("$.data.environmentSummary.shockLevelLabel", is("낮음")))
                .andExpect(jsonPath("$.data.activeSuggestion.id", is(5)));
    }

    @Test
    void getCareDashboard_noDiagnosisData_returns409() throws Exception {
        given(careQueryService.getDashboard(eq(1L), eq(20L)))
                .willThrow(new BusinessException(ErrorCode.NO_DIAGNOSIS_DATA));

        mockMvc.perform(get("/api/v1/products/20/care-dashboard")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("NO_DIAGNOSIS_DATA")));
    }

    @Test
    void getLatestReport_success_mixesStoredSnapshotAndLiveFields() throws Exception {
        given(careQueryService.getLatestReport(eq(1L), eq(20L))).willReturn(new CareReportResponse(
                CareConditionGrade.STABLE,
                "안정적인 상태입니다.",
                "최근 환경과 사용 기록이 권장 범위 안에 있습니다.",
                new CareReportResponse.EnvironmentSummary(
                        new CareReportResponse.PeriodMeasure(new BigDecimal("42.00"), "최근 30일", "이상적입니다"),
                        new CareReportResponse.PeriodMeasure(new BigDecimal("22.00"), "최근 30일", "이상적입니다"),
                        new CareReportResponse.DryExposure("최근 7일", "짧음")),
                new CareReportResponse.UsagePattern("1년 2개월", 50, 2),
                "현재는 안정적으로 유지되고 있습니다.",
                LocalDateTime.now()));

        mockMvc.perform(get("/api/v1/products/20/care-reports/latest")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.environmentSummary.dryExposure.label", is("짧음")))
                .andExpect(jsonPath("$.data.usagePattern.timeTogether", is("1년 2개월")));
    }

    @Test
    void getSensorSummary_invalidPeriod_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/products/20/sensor-summary")
                        .param("period", "3M")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_INPUT")));
    }

    @Test
    void getSensorSummary_thirtyDays_returnsTotalsNotMonthlyAverages() throws Exception {
        given(careQueryService.getSensorSummary(eq(1L), eq(20L), eq(SensorPeriod.THIRTY_DAYS)))
                .willReturn(new SensorSummaryResponse(
                        SensorPeriod.THIRTY_DAYS,
                        List.of(new SensorSummaryResponse.HumidityPoint(
                                LocalDate.of(2026, 7, 12), new BigDecimal("33.0"))),
                        new BigDecimal("22.0"), new BigDecimal("42.0"),
                        18, 2, null, null,
                        "이전 기간보다 습도 변화는 크지 않았고, 충격 감지는 안정적인 수준이었습니다.",
                        false));

        mockMvc.perform(get("/api/v1/products/20/sensor-summary")
                        .param("period", "30D")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period", is("30D")))
                .andExpect(jsonPath("$.data.outingCount", is(18)))
                .andExpect(jsonPath("$.data.outingCountMonthlyAvg").doesNotExist())
                .andExpect(jsonPath("$.data.insufficientHistory", is(false)));
    }

    @Test
    void getSensorSummary_oneYearWithoutHistory_returnsMonthlyAveragesAndSpecialText() throws Exception {
        given(careQueryService.getSensorSummary(eq(1L), eq(20L), eq(SensorPeriod.ONE_YEAR)))
                .willReturn(new SensorSummaryResponse(
                        SensorPeriod.ONE_YEAR, List.of(),
                        new BigDecimal("22.0"), new BigDecimal("42.0"),
                        null, null, 17, 2,
                        "MXIS와 함께한 지 아직 1년이 되지 않았습니다.",
                        true));

        mockMvc.perform(get("/api/v1/products/20/sensor-summary")
                        .param("period", "1Y")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period", is("1Y")))
                .andExpect(jsonPath("$.data.outingCountMonthlyAvg", is(17)))
                .andExpect(jsonPath("$.data.outingCount").doesNotExist())
                .andExpect(jsonPath("$.data.insufficientHistory", is(true)));
    }

    /**
     * 활성 제안이 없으면 에러가 아니라 성공 200이어야 한다.
     * ApiResponse가 @JsonInclude(NON_NULL)이라 data는 null 값 대신 키 자체가 빠진다
     * (로그아웃 응답이 {"success": true}인 것과 동일한 기존 컨벤션). 클라이언트 입장에서
     * 키 부재와 null은 동일하게 역직렬화된다.
     */
    @Test
    void getActiveSuggestion_none_returns200WithoutDataField() throws Exception {
        given(careSuggestionService.getActive(eq(1L), eq(20L))).willReturn(null);

        mockMvc.perform(get("/api/v1/products/20/care-suggestions/active")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void getActiveSuggestion_exists_returnsSuggestion() throws Exception {
        given(careSuggestionService.getActive(eq(1L), eq(20L))).willReturn(new CareSuggestionResponse(
                5L, 20L, "가벼운 점검을 제안드려요.", "누적 기록 기반", "가벼운 점검",
                LocalDate.of(2026, 8, 15), LocalDate.of(2026, 9, 15),
                CareSuggestionStatus.ACTIVE, false, LocalDateTime.now()));

        mockMvc.perform(get("/api/v1/products/20/care-suggestions/active")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(5)))
                .andExpect(jsonPath("$.data.recommendedService", is("가벼운 점검")));
    }
}
