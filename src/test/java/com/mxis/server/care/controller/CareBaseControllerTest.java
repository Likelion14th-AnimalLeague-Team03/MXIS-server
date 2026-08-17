package com.mxis.server.care.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mxis.server.care.dto.AiCareSummaryResponse;
import com.mxis.server.care.dto.CareEnvironmentResponse;
import com.mxis.server.care.dto.SensorPeriod;
import com.mxis.server.care.service.CareQueryService;
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
}
