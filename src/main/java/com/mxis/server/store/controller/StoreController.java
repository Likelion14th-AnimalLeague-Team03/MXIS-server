package com.mxis.server.store.controller;

import com.mxis.server.common.response.ApiResponse;
import com.mxis.server.store.dto.AvailableTimesResponse;
import com.mxis.server.store.dto.StoreResponse;
import com.mxis.server.store.service.StoreService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService storeService;

    @GetMapping
    public ApiResponse<List<StoreResponse>> getStores(
            @RequestParam(required = false) BigDecimal lat,
            @RequestParam(required = false) BigDecimal lng) {
        return ApiResponse.ok(storeService.getStores(lat, lng));
    }

    @GetMapping("/{id}/available-times")
    public ApiResponse<AvailableTimesResponse> getAvailableTimes(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(storeService.getAvailableTimes(id, date));
    }
}
