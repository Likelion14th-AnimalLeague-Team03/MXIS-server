package com.mxis.server.notification.repository;

import com.mxis.server.common.enums.NotificationType;
import com.mxis.server.notification.entity.Notification;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("""
            SELECT n FROM Notification n
            WHERE n.user.id = :userId
              AND (:type IS NULL OR n.notificationType = :type)
              AND (:unreadOnly = false OR n.read = false)
            ORDER BY n.createdAt DESC
            """)
    Page<Notification> findByUser(
            @Param("userId") Long userId,
            @Param("type") NotificationType type,
            @Param("unreadOnly") boolean unreadOnly,
            Pageable pageable);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndReadFalse(Long userId);

    boolean existsByUserIdAndNotificationTypeAndProductIdAndCreatedAtAfter(
            Long userId, NotificationType notificationType, Long productId, LocalDateTime createdAt);

    boolean existsByUserIdAndNotificationTypeAndDeviceIdAndCreatedAtAfter(
            Long userId, NotificationType notificationType, Long deviceId, LocalDateTime createdAt);

    boolean existsByUserIdAndNotificationTypeAndReservationId(
            Long userId, NotificationType notificationType, Long reservationId);

    boolean existsByUserIdAndNotificationTypeAndCareSuggestionId(
            Long userId, NotificationType notificationType, Long careSuggestionId);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Notification n
            SET n.read = true, n.readAt = :readAt
            WHERE n.user.id = :userId AND n.read = false
            """)
    int markAllRead(@Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);
}
