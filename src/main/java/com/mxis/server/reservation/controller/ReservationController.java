package com.mxis.server.reservation.controller;

import com.mxis.server.common.enums.ReservationStatus;
import com.mxis.server.common.response.ApiResponse;
import com.mxis.server.common.security.UserPrincipal;
import com.mxis.server.reservation.dto.ReservationCancelResponse;
import com.mxis.server.reservation.dto.ReservationCreateRequest;
import com.mxis.server.reservation.dto.ReservationResponse;
import com.mxis.server.reservation.dto.ReservationSummaryResponse;
import com.mxis.server.reservation.dto.ReservationUpdateRequest;
import com.mxis.server.reservation.service.ReservationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ReservationResponse> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ReservationCreateRequest request) {
        return ApiResponse.ok(reservationService.create(principal.userId(), request));
    }

    @GetMapping
    public ApiResponse<List<ReservationSummaryResponse>> getMyReservations(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) ReservationStatus status) {
        return ApiResponse.ok(reservationService.getMyReservations(principal.userId(), status));
    }

    @GetMapping("/{id}")
    public ApiResponse<ReservationResponse> getReservation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ApiResponse.ok(reservationService.getReservation(principal.userId(), id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<ReservationResponse> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody ReservationUpdateRequest request) {
        return ApiResponse.ok(reservationService.update(principal.userId(), id, request));
    }

    // 소프트 삭제(204)와 달리 취소는 결과 상태를 즉시 화면에 반영해야 해 200 + body로 반환한다.
    @DeleteMapping("/{id}")
    public ApiResponse<ReservationCancelResponse> cancel(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ApiResponse.ok(reservationService.cancel(principal.userId(), id));
    }
}
