package com.mxis.server.notification.scheduler;

import com.mxis.server.common.enums.NotificationType;
import com.mxis.server.common.enums.ReservationStatus;
import com.mxis.server.notification.service.PushNotificationService;
import com.mxis.server.reservation.entity.Reservation;
import com.mxis.server.reservation.repository.ReservationRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 매일 아침, 내일 방문 예정인 확정 예약에 리마인드 푸시를 보낸다. */
@Component
@RequiredArgsConstructor
public class ReservationReminderScheduler {

    private final ReservationRepository reservationRepository;
    private final PushNotificationService pushNotificationService;

    @Scheduled(cron = "0 0 9 * * *")
    @Transactional(readOnly = true)
    public void sendTomorrowReminders() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<Reservation> reservations = reservationRepository
                .findAllByReservedDateAndStatus(tomorrow, ReservationStatus.CONFIRMED);

        for (Reservation reservation : reservations) {
            pushNotificationService.notifyUser(reservation.getUser().getId(), NotificationType.RESERVATION,
                    "내일 예약이 있어요", "%s %s %s 방문 예약이 있습니다.".formatted(
                            reservation.getStore().getStoreName(), tomorrow, reservation.getReservedTime()));
        }
    }
}
