package com.mxis.server.care.job;

import com.mxis.server.care.service.CareDiagnosisJobService;
import com.mxis.server.care.service.CareDiagnosisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.Optional;

/** One synchronous job per tick: no unbounded executor queue, no transaction during AI I/O. */
@Slf4j
@Component
@RequiredArgsConstructor
public class CareDiagnosisWorker {
    private final CareDiagnosisJobService jobs;
    private final CareDiagnosisService diagnosis;

    @Value("${mxis.care.jobs.lease-seconds:300}")
    private int leaseSeconds = 300;

    @Scheduled(fixedDelayString = "${mxis.care.jobs.poll-delay-ms:1000}",
            initialDelayString = "${mxis.care.jobs.initial-delay-ms:5000}", scheduler = "careDiagnosisScheduler")
    public void runNext() {
        Optional<ClaimedDiagnosisJob> claimed;
        try {
            claimed = jobs.claimNext(Math.max(leaseSeconds, 1));
        } catch (TransientDataAccessException contention) {
            // The proxied claim transaction has already rolled back. Do not execute AI or
            // spin inside that failed transaction; the next scheduled tick can claim again.
            log.warn("진단 작업 선점을 다음 실행으로 미룹니다. errorType={}", contention.getClass().getSimpleName());
            return;
        }
        claimed.ifPresent(job -> {
            try {
                diagnosis.regenerate(job.productId(), job.period(), job.requestedAt());
                jobs.complete(job);
            } catch (RuntimeException failure) {
                jobs.fail(job, failure);
                log.warn("진단 작업 실패. jobId={}, attempt={}, errorType={}", job.id(), job.attemptCount(),
                        failure.getClass().getSimpleName());
            }
        });
    }
}
