package com.mxis.server.store.dto;

import com.mxis.server.store.entity.Store;
import java.math.BigDecimal;

public record StoreResponse(
        Long id,
        String storeName,
        String address,
        String phone,
        BigDecimal latitude,
        BigDecimal longitude,
        String openingHours,
        String storeUrl,
        BigDecimal distanceKm
) {
    public static StoreResponse from(Store store, BigDecimal distanceKm) {
        return new StoreResponse(
                store.getId(),
                store.getStoreName(),
                store.getAddress(),
                store.getPhone(),
                store.getLatitude(),
                store.getLongitude(),
                store.getOpeningHours(),
                store.getStoreUrl(),
                distanceKm);
    }
}
