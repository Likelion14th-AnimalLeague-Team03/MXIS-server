package com.mxis.server.notification.service;

import com.mxis.server.reservation.entity.Reservation;
import com.mxis.server.reservation.repository.ReservationRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReservationReminderScheduler {

    private static final int REMINDER_LOOKAHEAD_HOURS = 24;

    private final ReservationRepository reservationRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 */30 * * * *")
    public void createReservationReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime until = now.plusHours(REMINDER_LOOKAHEAD_HOURS);

        List<Reservation> reservations = reservationRepository.findConfirmedBetween(
                now.toLocalDate(), now.toLocalTime(),
                until.toLocalDate(), until.toLocalTime());

        for (Reservation reservation : reservations) {
            notificationService.createReservationReminderIfNeeded(reservation);
        }
    }
}
