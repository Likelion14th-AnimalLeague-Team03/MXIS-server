package com.mxis.server.care.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mxis.server.care.dto.CareSuggestionResponse;
import com.mxis.server.care.service.CareSuggestionService;
import com.mxis.server.common.enums.CareSuggestionStatus;
import com.mxis.server.common.exception.BusinessException;
import com.mxis.server.common.exception.ErrorCode;
import com.mxis.server.common.security.JwtAuthenticationFilter;
import com.mxis.server.common.security.JwtTokenProvider;
import com.mxis.server.config.SecurityConfig;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CareSuggestionController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class CareSuggestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CareSuggestionService careSuggestionService;

    private String accessToken;

    @BeforeEach
    void setUp() {
        accessToken = jwtTokenProvider.createAccessToken(1L, "user@mxis.com");
    }

    /** 상세 조회는 자동으로 읽음 처리되므로 응답의 isRead는 항상 true다. */
    @Test
    void getSuggestion_success_marksReadInResponse() throws Exception {
        given(careSuggestionService.getDetail(eq(1L), eq(5L))).willReturn(new CareSuggestionResponse(
                5L, 20L, "가벼운 점검을 제안드려요.", "누적 기록 기반", "가벼운 점검",
                LocalDate.of(2026, 8, 15), LocalDate.of(2026, 9, 15),
                CareSuggestionStatus.ACTIVE, true, LocalDateTime.now()));

        mockMvc.perform(get("/api/v1/care-suggestions/5")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productId", is(20)))
                .andExpect(jsonPath("$.data.isRead", is(true)));
    }

    @Test
    void getSuggestion_notOwned_returns403() throws Exception {
        given(careSuggestionService.getDetail(eq(1L), eq(5L)))
                .willThrow(new BusinessException(ErrorCode.CARE_SUGGESTION_NOT_OWNED));

        mockMvc.perform(get("/api/v1/care-suggestions/5")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("CARE_SUGGESTION_NOT_OWNED")));
    }

    @Test
    void markRead_success_returnsIdAndFlag() throws Exception {
        given(careSuggestionService.markRead(eq(1L), eq(5L)))
                .willReturn(new CareSuggestionResponse.ReadResult(5L, true));

        mockMvc.perform(patch("/api/v1/care-suggestions/5/read")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(5)))
                .andExpect(jsonPath("$.data.isRead", is(true)));
    }

    @Test
    void markRead_notFound_returns404() throws Exception {
        given(careSuggestionService.markRead(eq(1L), eq(99L)))
                .willThrow(new BusinessException(ErrorCode.CARE_SUGGESTION_NOT_FOUND));

        mockMvc.perform(patch("/api/v1/care-suggestions/99/read")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("CARE_SUGGESTION_NOT_FOUND")));
    }

    @Test
    void markRead_requiresAuth_returns401WithoutToken() throws Exception {
        mockMvc.perform(patch("/api/v1/care-suggestions/5/read"))
                .andExpect(status().isUnauthorized());
    }
}
