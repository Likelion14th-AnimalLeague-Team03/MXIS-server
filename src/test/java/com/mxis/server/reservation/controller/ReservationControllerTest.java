package com.mxis.server.reservation.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mxis.server.common.enums.ReservationStatus;
import com.mxis.server.common.enums.ReservationType;
import com.mxis.server.common.exception.BusinessException;
import com.mxis.server.common.exception.ErrorCode;
import com.mxis.server.common.security.JwtAuthenticationFilter;
import com.mxis.server.common.security.JwtTokenProvider;
import com.mxis.server.config.SecurityConfig;
import com.mxis.server.reservation.dto.ReservationCancelResponse;
import com.mxis.server.reservation.dto.ReservationCreateRequest;
import com.mxis.server.reservation.dto.ReservationResponse;
import com.mxis.server.reservation.dto.ReservationSummaryResponse;
import com.mxis.server.reservation.dto.ReservationUpdateRequest;
import com.mxis.server.reservation.service.ReservationService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReservationController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class ReservationControllerTest {

    private static final LocalDate RESERVED_DATE = LocalDate.of(2026, 8, 17);
    private static final LocalTime RESERVED_TIME = LocalTime.of(14, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private ReservationService reservationService;

    private String accessToken;

    @BeforeEach
    void setUp() {
        accessToken = jwtTokenProvider.createAccessToken(1L, "user@mxis.com");
    }

    private ReservationCreateRequest createRequest() {
        return new ReservationCreateRequest(20L, 1L, 5L, "가죽 컨디셔닝", ReservationType.FREE,
                RESERVED_DATE, RESERVED_TIME, "모서리 마모가 신경쓰여요");
    }

    private ReservationResponse sampleReservation() {
        return new ReservationResponse(100L, 20L, "MCM Aren Shopper", 1L, "MCM 청담 플래그십",
                "서울 강남구 압구정로 452", "02-1234-5678", 5L, "가죽 컨디셔닝", ReservationType.FREE,
                RESERVED_DATE, RESERVED_TIME, "모서리 마모가 신경쓰여요",
                ReservationStatus.CONFIRMED, null, null, LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    void create_requiresAuth_returns401WithoutToken() throws Exception {
        mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_success_returns201() throws Exception {
        given(reservationService.create(eq(1L), any(ReservationCreateRequest.class)))
                .willReturn(sampleReservation());

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id", is(100)))
                .andExpect(jsonPath("$.data.status", is("CONFIRMED")))
                // 명세상 시각은 "HH:mm" - Jackson 기본값인 "14:00:00"으로 나가면 안 된다.
                .andExpect(jsonPath("$.data.reservedTime", is("14:00")));
    }

    @Test
    void create_missingRequiredField_returns400() throws Exception {
        ReservationCreateRequest invalid = new ReservationCreateRequest(
                null, 1L, null, null, null, RESERVED_DATE, RESERVED_TIME, null);

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_INPUT")));
    }

    @Test
    void create_slotTaken_returns409() throws Exception {
        given(reservationService.create(eq(1L), any(ReservationCreateRequest.class)))
                .willThrow(new BusinessException(ErrorCode.SLOT_ALREADY_RESERVED));

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("SLOT_ALREADY_RESERVED")));
    }

    @Test
    void create_pastDate_returns400() throws Exception {
        given(reservationService.create(eq(1L), any(ReservationCreateRequest.class)))
                .willThrow(new BusinessException(ErrorCode.INVALID_INPUT, "지난 날짜로는 예약할 수 없습니다."));

        mockMvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_INPUT")));
    }

    @Test
    void getMyReservations_success_returnsSummaryList() throws Exception {
        given(reservationService.getMyReservations(eq(1L), eq(ReservationStatus.CONFIRMED)))
                .willReturn(List.of(new ReservationSummaryResponse(100L, 20L, "MCM Aren Shopper",
                        1L, "MCM 청담 플래그십", ReservationType.FREE, RESERVED_DATE, RESERVED_TIME,
                        ReservationStatus.CONFIRMED)));

        mockMvc.perform(get("/api/v1/reservations")
                        .param("status", "CONFIRMED")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].storeName", is("MCM 청담 플래그십")))
                .andExpect(jsonPath("$.data[0].reservedTime", is("14:00")));
    }

    @Test
    void getReservation_notOwned_returns403() throws Exception {
        given(reservationService.getReservation(eq(1L), eq(100L)))
                .willThrow(new BusinessException(ErrorCode.RESERVATION_NOT_OWNED));

        mockMvc.perform(get("/api/v1/reservations/100")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("RESERVATION_NOT_OWNED")));
    }

    @Test
    void update_cancelledReservation_returns409() throws Exception {
        given(reservationService.update(eq(1L), eq(100L), any(ReservationUpdateRequest.class)))
                .willThrow(new BusinessException(ErrorCode.RESERVATION_NOT_MODIFIABLE));

        mockMvc.perform(patch("/api/v1/reservations/100")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ReservationUpdateRequest(RESERVED_DATE, LocalTime.of(15, 0), null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("RESERVATION_NOT_MODIFIABLE")));
    }

    /** 취소는 다른 도메인의 소프트 삭제(204)와 달리 200 + 갱신된 상태를 반환한다. */
    @Test
    void cancel_success_returns200WithBody() throws Exception {
        LocalDateTime cancelledAt = LocalDateTime.of(2026, 8, 11, 9, 10);
        given(reservationService.cancel(eq(1L), eq(100L)))
                .willReturn(new ReservationCancelResponse(100L, ReservationStatus.CANCELLED, cancelledAt));

        mockMvc.perform(delete("/api/v1/reservations/100")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("CANCELLED")))
                .andExpect(jsonPath("$.data.cancelledAt", is("2026-08-11T09:10:00")));
    }

    @Test
    void cancel_notFound_returns404() throws Exception {
        given(reservationService.cancel(eq(1L), eq(999L)))
                .willThrow(new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));

        mockMvc.perform(delete("/api/v1/reservations/999")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("RESERVATION_NOT_FOUND")));
    }
}
