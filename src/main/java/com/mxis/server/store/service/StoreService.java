package com.mxis.server.store.service;

import com.mxis.server.common.exception.BusinessException;
import com.mxis.server.common.exception.ErrorCode;
import com.mxis.server.reservation.repository.ReservationRepository;
import com.mxis.server.store.dto.AvailableTimesResponse;
import com.mxis.server.store.dto.StoreResponse;
import com.mxis.server.store.entity.Store;
import com.mxis.server.store.repository.StoreRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreService {

    private static final DateTimeFormatter SLOT_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final double EARTH_RADIUS_KM = 6371.0;

    private final StoreRepository storeRepository;
    private final ReservationRepository reservationRepository;

    /**
     * 매장 목록. lat/lng가 둘 다 있을 때만 거리를 계산해 가까운 순으로 정렬하고,
     * 하나만 오거나 없으면 거리 계산 없이 id 오름차순으로 반환한다.
     */
    public List<StoreResponse> getStores(BigDecimal lat, BigDecimal lng) {
        List<Store> stores = storeRepository.findAllByIsActiveTrueOrderByIdAsc();

        if (lat == null || lng == null) {
            return stores.stream()
                    .map(store -> StoreResponse.from(store, null))
                    .toList();
        }

        // ponytail: 매장 수가 MVP 기준 수십 개 수준이라 전 건 조회 후 애플리케이션에서 계산·정렬한다.
        // 매장이 수천 개가 되면 DB 공간 함수(ST_Distance_Sphere)로 옮긴다.
        return stores.stream()
                .map(store -> StoreResponse.from(store, distanceKm(lat, lng, store)))
                .sorted(Comparator.comparing(
                        StoreResponse::distanceKm,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /**
     * 특정 매장/날짜의 예약 슬롯과 가능 여부. 슬롯 구간은 매장 운영시간(open_time~close_time)을 따른다.
     * 과거 날짜도 그대로 계산해서 반환한다 (조회는 부작용이 없어 막지 않는다 - 예약 생성/변경에서만 막음).
     */
    public AvailableTimesResponse getAvailableTimes(Long storeId, LocalDate date) {
        Store store = storeRepository.findByIdAndIsActiveTrue(storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));

        Set<LocalTime> taken = Set.copyOf(reservationRepository.findConfirmedTimes(storeId, date));

        List<AvailableTimesResponse.TimeSlot> slots = store.bookableSlots().stream()
                .map(time -> new AvailableTimesResponse.TimeSlot(
                        time.format(SLOT_FORMAT), !taken.contains(time)))
                .toList();

        return new AvailableTimesResponse(store.getId(), date, slots);
    }

    private BigDecimal distanceKm(BigDecimal lat, BigDecimal lng, Store store) {
        if (!store.hasCoordinates()) {
            return null;
        }
        double lat1 = Math.toRadians(lat.doubleValue());
        double lat2 = Math.toRadians(store.getLatitude().doubleValue());
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(store.getLongitude().subtract(lng).doubleValue());

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double km = 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));

        return BigDecimal.valueOf(km).setScale(1, RoundingMode.HALF_UP);
    }
}
