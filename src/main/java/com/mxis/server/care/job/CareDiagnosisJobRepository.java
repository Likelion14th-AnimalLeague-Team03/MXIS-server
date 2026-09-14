package com.mxis.server.care.job;

import com.mxis.server.care.dto.SensorPeriod;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** MariaDB atomic updates keep queue operations safe across application instances. */
@Repository
@RequiredArgsConstructor
public class CareDiagnosisJobRepository {
    static final int MAX_ATTEMPTS = 3;
    private final JdbcTemplate jdbc;

    public void request(Long productId, SensorPeriod period, LocalDateTime requestedAt) {
        // Only terminal jobs can be requested again. GETs during in-flight work coalesce;
        // FAILED jobs have a cooldown, so a polling client cannot create unlimited AI retries.
        jdbc.update("""
                INSERT INTO care_diagnosis_jobs (product_id, analysis_period, requested_at)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  requested_version = IF(status IN ('DONE','FAILED') AND available_at <= CURRENT_TIMESTAMP(6),
                                         requested_version + 1, requested_version),
                  requested_at = IF(status IN ('DONE','FAILED') AND available_at <= CURRENT_TIMESTAMP(6),
                                    GREATEST(requested_at, VALUES(requested_at)), requested_at),
                  attempt_count = IF(status IN ('DONE','FAILED') AND available_at <= CURRENT_TIMESTAMP(6),
                                     0, attempt_count),
                  status = IF(status IN ('DONE','FAILED') AND available_at <= CURRENT_TIMESTAMP(6), 'PENDING', status),
                  updated_at = CURRENT_TIMESTAMP(6)
                """, productId, period.code(), requestedAt);
    }

    public void requestAfterSensorChange(Long productId, LocalDateTime requestedAt) {
        // Refresh periods that have already been requested. Always keep a 30D home diagnosis.
        jdbc.update("""
                UPDATE care_diagnosis_jobs SET
                  requested_version = requested_version + 1,
                  requested_at = GREATEST(requested_at, ?),
                  attempt_count = IF(status = 'RUNNING', attempt_count, 0),
                  status = IF(status = 'RUNNING', 'RUNNING', 'PENDING'),
                  available_at = CURRENT_TIMESTAMP(6), updated_at = CURRENT_TIMESTAMP(6)
                WHERE product_id = ? AND analysis_period <> '30D'
                """, requestedAt, productId);
        jdbc.update("""
                INSERT INTO care_diagnosis_jobs (product_id, analysis_period, requested_at)
                VALUES (?, '30D', ?)
                ON DUPLICATE KEY UPDATE
                  requested_version = requested_version + 1,
                  requested_at = GREATEST(requested_at, VALUES(requested_at)),
                  attempt_count = IF(status = 'RUNNING', attempt_count, 0),
                  status = IF(status = 'RUNNING', 'RUNNING', 'PENDING'),
                  available_at = CURRENT_TIMESTAMP(6), updated_at = CURRENT_TIMESTAMP(6)
                """, productId, requestedAt);
    }

    public Optional<ClaimedDiagnosisJob> claimNext(int leaseSeconds) {
        // An instance that crashes on its final attempt must not leave a permanently RUNNING row.
        jdbc.update("""
                UPDATE care_diagnosis_jobs SET
                  status = IF(requested_version > claimed_version, 'PENDING', 'FAILED'),
                  attempt_count = IF(requested_version > claimed_version, 0, attempt_count),
                  available_at = IF(requested_version > claimed_version, CURRENT_TIMESTAMP(6),
                                    TIMESTAMPADD(MINUTE, 15, CURRENT_TIMESTAMP(6))),
                  lease_token = NULL, lease_until = NULL,
                  last_error = 'Worker lease expired after final attempt', updated_at = CURRENT_TIMESTAMP(6)
                WHERE status = 'RUNNING' AND lease_until <= CURRENT_TIMESTAMP(6) AND attempt_count >= ?
                """, MAX_ATTEMPTS);
        List<Long> candidates = jdbc.queryForList("""
                SELECT id FROM care_diagnosis_jobs
                WHERE attempt_count < ? AND (
                  (status = 'PENDING' AND available_at <= CURRENT_TIMESTAMP(6)) OR
                  (status = 'RUNNING' AND lease_until <= CURRENT_TIMESTAMP(6)))
                ORDER BY available_at, id LIMIT 20
                """, Long.class, MAX_ATTEMPTS);
        for (Long id : candidates) {
            String token = UUID.randomUUID().toString();
            int changed = jdbc.update("""
                    UPDATE care_diagnosis_jobs SET
                      status = 'RUNNING', claimed_version = requested_version, claimed_at = requested_at,
                      attempt_count = attempt_count + 1, lease_token = ?,
                      lease_until = TIMESTAMPADD(SECOND, ?, CURRENT_TIMESTAMP(6)), updated_at = CURRENT_TIMESTAMP(6)
                    WHERE id = ? AND attempt_count < ? AND (
                      (status = 'PENDING' AND available_at <= CURRENT_TIMESTAMP(6)) OR
                      (status = 'RUNNING' AND lease_until <= CURRENT_TIMESTAMP(6)))
                    """, token, leaseSeconds, id, MAX_ATTEMPTS);
            if (changed == 1) {
                return jdbc.query("""
                        SELECT id, product_id, analysis_period, claimed_at, lease_token, attempt_count
                        FROM care_diagnosis_jobs WHERE id = ? AND lease_token = ?
                        """, (rs, row) -> new ClaimedDiagnosisJob(rs.getLong("id"), rs.getLong("product_id"),
                        SensorPeriod.fromCode(rs.getString("analysis_period")),
                        rs.getTimestamp("claimed_at").toLocalDateTime(), rs.getString("lease_token"),
                        rs.getInt("attempt_count")), id, token).stream().findFirst();
            }
        }
        return Optional.empty();
    }

    public boolean complete(ClaimedDiagnosisJob job) {
        return jdbc.update("""
                UPDATE care_diagnosis_jobs SET
                  status = IF(requested_version > claimed_version, 'PENDING', 'DONE'),
                  attempt_count = IF(requested_version > claimed_version, 0, attempt_count),
                  available_at = CURRENT_TIMESTAMP(6), lease_token = NULL, lease_until = NULL,
                  last_error = NULL, updated_at = CURRENT_TIMESTAMP(6)
                WHERE id = ? AND status = 'RUNNING' AND lease_token = ?
                """, job.id(), job.leaseToken()) == 1;
    }

    public boolean fail(ClaimedDiagnosisJob job, String errorType) {
        int retryDelaySeconds = 30 * (1 << Math.min(job.attemptCount() - 1, 5));
        return jdbc.update("""
                UPDATE care_diagnosis_jobs SET
                  available_at = CASE WHEN requested_version > claimed_version THEN CURRENT_TIMESTAMP(6)
                    WHEN attempt_count >= ? THEN TIMESTAMPADD(MINUTE, 15, CURRENT_TIMESTAMP(6))
                    ELSE TIMESTAMPADD(SECOND, ?, CURRENT_TIMESTAMP(6)) END,
                  status = IF(requested_version > claimed_version OR attempt_count < ?, 'PENDING', 'FAILED'),
                  attempt_count = IF(requested_version > claimed_version, 0, attempt_count),
                  lease_token = NULL, lease_until = NULL, last_error = ?, updated_at = CURRENT_TIMESTAMP(6)
                WHERE id = ? AND status = 'RUNNING' AND lease_token = ?
                """, MAX_ATTEMPTS, retryDelaySeconds, MAX_ATTEMPTS, errorType, job.id(), job.leaseToken()) == 1;
    }
}
