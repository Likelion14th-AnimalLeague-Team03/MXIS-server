package com.mxis.server.care.service;

import com.mxis.server.care.dto.SensorPeriod;
import com.mxis.server.care.job.CareDiagnosisJobRepository;
import com.mxis.server.care.job.ClaimedDiagnosisJob;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CareDiagnosisJobService {
    private final CareDiagnosisJobRepository repository;

    @Transactional
    public void request(Long productId, SensorPeriod period, LocalDateTime requestedAt) {
        repository.request(productId, period, requestedAt);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requestAfterSensorChange(Long productId, LocalDateTime requestedAt) {
        repository.requestAfterSensorChange(productId, requestedAt);
    }

    // The lease-expiry scan and CAS must not retain REPEATABLE_READ next-key locks.
    // Two workers otherwise lock the same empty RUNNING index range, then deadlock
    // when their PENDING -> RUNNING updates need to insert an index entry there.
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public Optional<ClaimedDiagnosisJob> claimNext(int leaseSeconds) {
        return repository.claimNext(leaseSeconds);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean complete(ClaimedDiagnosisJob job) {
        return repository.complete(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean fail(ClaimedDiagnosisJob job, RuntimeException failure) {
        // Persist only the error class; exception messages can include sensitive upstream bodies.
        return repository.fail(job, failure.getClass().getSimpleName());
    }
}
