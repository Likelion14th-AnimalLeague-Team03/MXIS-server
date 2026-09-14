package com.mxis.server.care.job;

import com.mxis.server.care.dto.SensorPeriod;
import java.time.LocalDateTime;

/** Values captured by the short claim transaction; safe to pass outside a JPA session. */
public record ClaimedDiagnosisJob(Long id, Long productId, SensorPeriod period, LocalDateTime requestedAt,
                                 String leaseToken, int attemptCount) {
}
