package com.mxis.server.store.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 예약 슬롯은 매장 운영시간에서 파생되므로, 시간대가 다른 매장들에 대해 경계를 직접 확인한다.
 * Store는 관리자 API가 없어 생성자가 없는 엔티티라, 테스트에서는 필드를 직접 주입한다.
 */
class StoreTest {

    private static Store storeOpen(String open, String close) {
        Store store = new Store();
        ReflectionTestUtils.setField(store, "openTime", LocalTime.parse(open));
        ReflectionTestUtils.setField(store, "closeTime", LocalTime.parse(close));
        return store;
    }

    @Test
    void bookableSlots_lastSlotIsHalfHourBeforeClosing() {
        List<LocalTime> slots = storeOpen("11:00", "20:00").bookableSlots();

        assertThat(slots).hasSize(18)
                .startsWith(LocalTime.of(11, 0))
                .endsWith(LocalTime.of(19, 30))
                // 마감 시각에 시작하는 예약은 만들지 않는다.
                .doesNotContain(LocalTime.of(20, 0));
    }

    @Test
    void bookableSlots_respectsPerStoreOpeningTime() {
        assertThat(storeOpen("10:30", "20:00").bookableSlots())
                .hasSize(19)
                .startsWith(LocalTime.of(10, 30))
                .endsWith(LocalTime.of(19, 30));

        assertThat(storeOpen("10:00", "19:00").bookableSlots())
                .hasSize(18)
                .startsWith(LocalTime.of(10, 0))
                .endsWith(LocalTime.of(18, 30));
    }

    @Test
    void isBookableAt_rejectsOffGridAndOutOfHours() {
        Store store = storeOpen("11:00", "20:00");

        assertThat(store.isBookableAt(LocalTime.of(11, 0))).isTrue();
        assertThat(store.isBookableAt(LocalTime.of(19, 30))).isTrue();
        assertThat(store.isBookableAt(LocalTime.of(11, 15))).isFalse(); // 30분 단위 아님
        assertThat(store.isBookableAt(LocalTime.of(10, 30))).isFalse(); // 개점 전
        assertThat(store.isBookableAt(LocalTime.of(20, 0))).isFalse();  // 마감 시각
    }

    /** close_time이 open_time보다 이른 비정상 데이터에서도 무한 루프 없이 빈 목록이어야 한다. */
    @Test
    void bookableSlots_invalidRange_returnsEmpty() {
        assertThat(storeOpen("20:00", "10:00").bookableSlots()).isEmpty();
    }
}
