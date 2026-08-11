package com.mxis.server.store.entity;

import com.mxis.server.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * MCM 매장. 소프트 삭제 대상이 아니며, 예약을 받을 수 있는지는 is_active로만 판단한다.
 * 예약 가능 시간은 매장마다 다르므로 슬롯 계산도 이 엔티티가 담당한다.
 */
@Getter
@Entity
@Table(name = "stores")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Store extends BaseTimeEntity {

    /** 예약 슬롯 간격(분). 전 매장 공통. */
    private static final int SLOT_MINUTES = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_name", nullable = false, length = 100)
    private String storeName;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(length = 20)
    private String phone;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    /** 사람이 읽는 안내 문구. 파싱하지 않으며, 예약 슬롯 계산은 openTime/closeTime을 쓴다. */
    @Column(name = "opening_hours", length = 255)
    private String openingHours;

    @Column(name = "open_time", nullable = false)
    private LocalTime openTime;

    @Column(name = "close_time", nullable = false)
    private LocalTime closeTime;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }

    /**
     * 예약 가능한 30분 단위 슬롯. openTime부터 시작하며 마지막 슬롯은 closeTime 30분 전이다
     * (마감 시각에 시작하는 예약은 만들지 않는다). 요일 제한은 두지 않고 매장 운영시간만 따른다.
     *
     * 슬롯 개수를 산술로 구해 closeTime이 openTime보다 이른 비정상 데이터에서도 빈 목록을 반환한다.
     */
    public List<LocalTime> bookableSlots() {
        long openMinutes = Duration.between(openTime, closeTime).toMinutes();
        int slotCount = (int) (openMinutes / SLOT_MINUTES);

        return IntStream.range(0, Math.max(slotCount, 0))
                .mapToObj(i -> openTime.plusMinutes((long) i * SLOT_MINUTES))
                .toList();
    }

    public boolean isBookableAt(LocalTime time) {
        return bookableSlots().contains(time);
    }
}
