package com.mxis.server.reservation.repository;

import com.mxis.server.common.enums.ReservationStatus;
import com.mxis.server.reservation.entity.Reservation;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /** 해당 매장/날짜에 이미 확정되어 슬롯을 점유 중인 시각들. */
    @Query("""
            SELECT r.reservedTime FROM Reservation r
            WHERE r.store.id = :storeId AND r.reservedDate = :date
              AND r.status = com.mxis.server.common.enums.ReservationStatus.CONFIRMED
            """)
    List<LocalTime> findConfirmedTimes(@Param("storeId") Long storeId, @Param("date") LocalDate date);

    /**
     * 슬롯 사전 점유 확인. 예약 변경 시 자기 자신은 제외해야 하므로 excludeId를 받는다
     * (신규 생성 시에는 존재할 수 없는 id인 -1 등을 넘긴다).
     */
    @Query("""
            SELECT COUNT(r) > 0 FROM Reservation r
            WHERE r.store.id = :storeId AND r.reservedDate = :date AND r.reservedTime = :time
              AND r.status = com.mxis.server.common.enums.ReservationStatus.CONFIRMED
              AND r.id <> :excludeId
            """)
    boolean existsConfirmedSlot(@Param("storeId") Long storeId, @Param("date") LocalDate date,
                                @Param("time") LocalTime time, @Param("excludeId") Long excludeId);

    @Query("""
            SELECT r FROM Reservation r
            WHERE r.user.id = :userId AND (:status IS NULL OR r.status = :status)
            ORDER BY r.reservedDate ASC, r.reservedTime ASC
            """)
    List<Reservation> findAllByUser(@Param("userId") Long userId, @Param("status") ReservationStatus status);

    @Query("""
            SELECT r FROM Reservation r
            JOIN FETCH r.user
            JOIN FETCH r.product
            JOIN FETCH r.store
            WHERE r.status = com.mxis.server.common.enums.ReservationStatus.CONFIRMED
              AND (r.reservedDate > :fromDate OR (r.reservedDate = :fromDate AND r.reservedTime >= :fromTime))
              AND (r.reservedDate < :toDate OR (r.reservedDate = :toDate AND r.reservedTime < :toTime))
            ORDER BY r.reservedDate ASC, r.reservedTime ASC
            """)
    List<Reservation> findConfirmedBetween(@Param("fromDate") LocalDate fromDate,
                                           @Param("fromTime") LocalTime fromTime,
                                           @Param("toDate") LocalDate toDate,
                                           @Param("toTime") LocalTime toTime);
}
