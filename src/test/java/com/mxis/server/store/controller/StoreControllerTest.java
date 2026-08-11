package com.mxis.server.store.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mxis.server.common.exception.BusinessException;
import com.mxis.server.common.exception.ErrorCode;
import com.mxis.server.common.security.JwtAuthenticationFilter;
import com.mxis.server.common.security.JwtTokenProvider;
import com.mxis.server.config.SecurityConfig;
import com.mxis.server.store.dto.AvailableTimesResponse;
import com.mxis.server.store.dto.StoreResponse;
import com.mxis.server.store.service.StoreService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(StoreController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class StoreControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private StoreService storeService;

    private String accessToken;

    @BeforeEach
    void setUp() {
        accessToken = jwtTokenProvider.createAccessToken(1L, "user@mxis.com");
    }

    private StoreResponse sampleStore(BigDecimal distanceKm) {
        return new StoreResponse(1L, "MCM 청담 플래그십", "서울 강남구 압구정로 452", "02-1234-5678",
                new BigDecimal("37.5262"), new BigDecimal("127.0396"), "월-금 10:00-19:00", distanceKm);
    }

    @Test
    void getStores_requiresAuth_returns401WithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/stores"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getStores_withoutCoordinates_returnsNullDistance() throws Exception {
        given(storeService.getStores(null, null)).willReturn(List.of(sampleStore(null)));

        mockMvc.perform(get("/api/v1/stores").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].storeName", is("MCM 청담 플래그십")))
                .andExpect(jsonPath("$.data[0].distanceKm").doesNotExist());
    }

    @Test
    void getStores_withCoordinates_returnsDistance() throws Exception {
        given(storeService.getStores(any(BigDecimal.class), any(BigDecimal.class)))
                .willReturn(List.of(sampleStore(new BigDecimal("1.2"))));

        mockMvc.perform(get("/api/v1/stores")
                        .param("lat", "37.5172")
                        .param("lng", "127.0473")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].distanceKm", is(1.2)));
    }

    @Test
    void getAvailableTimes_success_returnsSlots() throws Exception {
        given(storeService.getAvailableTimes(eq(1L), eq(LocalDate.of(2026, 8, 17))))
                .willReturn(new AvailableTimesResponse(1L, LocalDate.of(2026, 8, 17), List.of(
                        new AvailableTimesResponse.TimeSlot("10:00", true),
                        new AvailableTimesResponse.TimeSlot("10:30", false))));

        mockMvc.perform(get("/api/v1/stores/1/available-times")
                        .param("date", "2026-08-17")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slots[0].time", is("10:00")))
                .andExpect(jsonPath("$.data.slots[0].available", is(true)))
                .andExpect(jsonPath("$.data.slots[1].available", is(false)));
    }

    @Test
    void getAvailableTimes_missingDate_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/stores/1/available-times")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAvailableTimes_storeNotFound_returns404() throws Exception {
        given(storeService.getAvailableTimes(eq(99L), any(LocalDate.class)))
                .willThrow(new BusinessException(ErrorCode.STORE_NOT_FOUND));

        mockMvc.perform(get("/api/v1/stores/99/available-times")
                        .param("date", "2026-08-17")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("STORE_NOT_FOUND")));
    }
}
