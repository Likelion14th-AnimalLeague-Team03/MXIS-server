package com.mxis.server.care.job;

import static org.mockito.Mockito.*;

import com.mxis.server.care.dto.SensorPeriod;
import com.mxis.server.care.service.CareDiagnosisJobService;
import com.mxis.server.care.service.CareDiagnosisService;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;

class CareDiagnosisWorkerTest {
    private final CareDiagnosisJobService jobs = mock(CareDiagnosisJobService.class);
    private final CareDiagnosisService diagnosis = mock(CareDiagnosisService.class);
    private final CareDiagnosisWorker worker = new CareDiagnosisWorker(jobs, diagnosis);
    private final ClaimedDiagnosisJob job = new ClaimedDiagnosisJob(1L, 2L, SensorPeriod.THIRTY_DAYS,
            LocalDateTime.of(2026, 9, 14, 12, 0), "lease-token", 1);

    @Test
    void transientClaimFailureDefersToNextTickWithoutCallingAi() {
        when(jobs.claimNext(300)).thenThrow(new CannotAcquireLockException("competing transaction"))
                .thenReturn(Optional.of(job));
        worker.runNext();
        verifyNoInteractions(diagnosis);
        verify(jobs, never()).fail(any(), any());
        worker.runNext();
        verify(diagnosis).regenerate(job.productId(), job.period(), job.requestedAt());
        verify(jobs).complete(job);
    }

    @Test
    void externalFailureIsRecordedForRetryWithoutCompletingJob() {
        when(jobs.claimNext(300)).thenReturn(Optional.of(job));
        RuntimeException failure = new IllegalStateException("upstream unavailable");
        doThrow(failure).when(diagnosis).regenerate(job.productId(), job.period(), job.requestedAt());
        worker.runNext();
        verify(jobs).fail(job, failure);
        verify(jobs, never()).complete(any());
    }

    @Test
    void successfulRegenerationCompletesClaimedLease() {
        when(jobs.claimNext(300)).thenReturn(Optional.of(job));
        worker.runNext();
        var order = inOrder(jobs, diagnosis);
        order.verify(jobs).claimNext(300);
        order.verify(diagnosis).regenerate(job.productId(), job.period(), job.requestedAt());
        order.verify(jobs).complete(job);
    }

    @Test
    void noCommittedClaimMeansNoAiWork() {
        when(jobs.claimNext(300)).thenReturn(Optional.empty());
        worker.runNext();
        verifyNoInteractions(diagnosis);
    }
}
