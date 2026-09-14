package com.mxis.server.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.mxis.server.care.dto.SensorPeriod;
import com.mxis.server.care.job.CareDiagnosisJobRepository;
import com.mxis.server.care.job.CareDiagnosisWorker;
import com.mxis.server.care.job.ClaimedDiagnosisJob;
import com.mxis.server.care.service.CareDiagnosisJobService;
import com.mxis.server.care.service.CareDiagnosisService;
import com.mxis.server.common.enums.ProductDeviceRole;
import com.mxis.server.config.JpaAuditingConfig;
import com.mxis.server.device.entity.Device;
import com.mxis.server.device.repository.DeviceRepository;
import com.mxis.server.notification.service.NotificationService;
import com.mxis.server.product.entity.Product;
import com.mxis.server.product.entity.ProductDevice;
import com.mxis.server.product.repository.ProductDeviceRepository;
import com.mxis.server.product.repository.ProductRepository;
import com.mxis.server.product.service.ProductDeviceMutationLock;
import com.mxis.server.sensor.dto.SensorReadingBatchRequest;
import com.mxis.server.sensor.dto.SensorReadingBatchResponse;
import com.mxis.server.sensor.dto.SensorReadingItem;
import com.mxis.server.sensor.service.SensorReadingService;
import com.mxis.server.user.entity.User;
import com.mxis.server.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("integration")
@DataJpaTest
@ActiveProfiles("integration")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, SensorReadingService.class, ProductDeviceMutationLock.class,
        CareDiagnosisJobRepository.class, CareDiagnosisJobService.class, SensorPipelineMariaDbIntegrationTest.TimeConfig.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SensorPipelineMariaDbIntegrationTest {
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private ProductRepository products;
    @Autowired private DeviceRepository devices;
    @Autowired private ProductDeviceRepository links;
    @Autowired private SensorReadingService sensor;
    @Autowired private CareDiagnosisJobService jobs;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockBean private NotificationService notifications;
    private Fixture fixture;
    private TransactionTemplate tx;
    private final LocalDateTime measuredAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"))
            .minusHours(1).truncatedTo(ChronoUnit.MICROS);

    @TestConfiguration
    static class TimeConfig {
        @Bean Clock clock() { return Clock.system(ZoneId.of("Asia/Seoul")); }
    }

    @BeforeEach
    void createCommittedFixture() {
        tx = new TransactionTemplate(transactionManager);
        fixture = tx.execute(status -> {
            String unique = UUID.randomUUID().toString();
            User user = users.saveAndFlush(User.createLocal(unique + "@mxis.example", "hash", "Sensor test", null));
            Product product = products.saveAndFlush(new Product(user, unique, "Bag", null, "natural_leather",
                    "Natural leather", List.of(), null, null, null, null));
            Device device = devices.saveAndFlush(new Device(user, unique, "Charm", null, null, null));
            ProductDevice link = new ProductDevice(product, device, ProductDeviceRole.SECONDARY);
            ReflectionTestUtils.setField(link, "attachedAt", measuredAt.minusDays(1));
            links.saveAndFlush(link);
            return new Fixture(user.getId(), product.getId(), device.getId(), link.getId());
        });
    }

    @AfterEach
    void removeOnlyThisTestsFixture() {
        if (fixture == null) return;
        tx.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM care_diagnosis_jobs WHERE product_id IN (SELECT id FROM products WHERE user_id = ?)", fixture.userId());
            jdbc.update("DELETE FROM sensor_readings WHERE device_id = ?", fixture.deviceId());
            jdbc.update("DELETE FROM product_devices WHERE device_id = ?", fixture.deviceId());
            jdbc.update("DELETE FROM devices WHERE id = ?", fixture.deviceId());
            jdbc.update("DELETE FROM products WHERE user_id = ?", fixture.userId());
            jdbc.update("DELETE FROM users WHERE id = ?", fixture.userId());
        });
    }

    @Test
    void microsecondTimestampAndDecimalScaleReplayRoundTripExactly() {
        LocalDateTime precise = measuredAt.withNano(123456000);
        SensorReadingBatchRequest first = new SensorReadingBatchRequest(List.of(new SensorReadingItem(999L,
                new BigDecimal("20.00"), new BigDecimal("50.0"), null, 0, false, precise)));
        SensorReadingBatchRequest replay = new SensorReadingBatchRequest(List.of(new SensorReadingItem(999L,
                new BigDecimal("20"), new BigDecimal("50.00"), null, 0, false, precise.plusNanos(999))));
        assertThat(sensor.syncBatch(fixture.userId(), fixture.deviceId(), first).savedCount()).isEqualTo(1);
        assertThat(sensor.syncBatch(fixture.userId(), fixture.deviceId(), replay).duplicateCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT measured_at FROM sensor_readings WHERE device_id = ?", java.sql.Timestamp.class,
                fixture.deviceId()).toLocalDateTime()).isEqualTo(precise);
    }

    @Test
    void simultaneousReplayCommitsExactlyOneMeasurement() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> { start.await(); return upload(100L); });
            var second = pool.submit(() -> { start.await(); return upload(100L); });
            start.countDown();
            var a = first.get(15, TimeUnit.SECONDS);
            var b = second.get(15, TimeUnit.SECONDS);
            assertThat(a.savedCount() + b.savedCount()).isEqualTo(1);
            assertThat(a.duplicateCount() + b.duplicateCount()).isEqualTo(1);
            assertThat(countReadings()).isEqualTo(1);
            assertThat(value("requested_version", Long.class)).isEqualTo(1L);
        } finally {
            pool.shutdownNow();
        }
    }

    @RepeatedTest(10)
    void simultaneousWorkersCanClaimAJobOnlyOnce() throws Exception {
        jobs.request(fixture.productId(), SensorPeriod.THIRTY_DAYS, measuredAt);
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> { start.await(); return jobs.claimNext(300); });
            var second = pool.submit(() -> { start.await(); return jobs.claimNext(300); });
            start.countDown();
            var a = first.get(15, TimeUnit.SECONDS);
            var b = second.get(15, TimeUnit.SECONDS);
            assertThat((a.isPresent() ? 1 : 0) + (b.isPresent() ? 1 : 0)).isEqualTo(1);
            assertThat(value("attempt_count", Integer.class)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void delayedMeasurementsRemainWithOriginalProductAfterRelinking() {
        long newProductId = tx.execute(status -> {
            jdbc.update("UPDATE product_devices SET detached_at = ? WHERE id = ?", measuredAt.plusMinutes(5), fixture.linkId());
            User user = users.findById(fixture.userId()).orElseThrow();
            Product next = products.saveAndFlush(new Product(user, UUID.randomUUID().toString(), "New bag", null,
                    "natural_leather", "Natural leather", List.of(), null, null, null, null));
            ProductDevice nextLink = new ProductDevice(next, devices.findById(fixture.deviceId()).orElseThrow(), ProductDeviceRole.SECONDARY);
            ReflectionTestUtils.setField(nextLink, "attachedAt", measuredAt.plusMinutes(5));
            links.saveAndFlush(nextLink);
            return next.getId();
        });
        assertThat(upload(100L).savedCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT product_id FROM sensor_readings WHERE device_id = ?", Long.class,
                fixture.deviceId())).isEqualTo(fixture.productId()).isNotEqualTo(newProductId);
    }

    @Test
    void databaseRejectsTwoActiveProductsForOneDevice() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            ProductDevice duplicate = new ProductDevice(products.findById(fixture.productId()).orElseThrow(),
                    devices.findById(fixture.deviceId()).orElseThrow(), ProductDeviceRole.SECONDARY);
            links.saveAndFlush(duplicate);
        })).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void queueCoalescesGetRequestsButRetainsMeasurementsArrivingDuringWork() {
        LocalDateTime firstAt = measuredAt.plusMinutes(1);
        jobs.request(fixture.productId(), SensorPeriod.THIRTY_DAYS, firstAt);
        jobs.request(fixture.productId(), SensorPeriod.THIRTY_DAYS, firstAt.plusMinutes(1));
        ClaimedDiagnosisJob first = jobs.claimNext(300).orElseThrow();
        assertThat(first.requestedAt()).isEqualTo(firstAt);
        assertThat(jobs.claimNext(300)).isEmpty();
        jobs.request(fixture.productId(), SensorPeriod.THIRTY_DAYS, firstAt.plusMinutes(2));
        assertThat(value("requested_version", Long.class)).isEqualTo(1L);

        tx.executeWithoutResult(status -> jobs.requestAfterSensorChange(fixture.productId(), firstAt.plusMinutes(3)));
        assertThat(jobs.complete(first)).isTrue();
        assertThat(value("status", String.class)).isEqualTo("PENDING");
        ClaimedDiagnosisJob second = jobs.claimNext(300).orElseThrow();
        assertThat(second.requestedAt()).isEqualTo(firstAt.plusMinutes(3));
        assertThat(second.attemptCount()).isEqualTo(1);
        assertThat(jobs.complete(first)).isFalse();
        assertThat(jobs.complete(second)).isTrue();
        assertThat(value("status", String.class)).isEqualTo("DONE");
    }

    @Test
    void aiFailureDoesNotRollBackCommittedSensorAndRetriesAreBounded() {
        assertThat(upload(100L).savedCount()).isEqualTo(1);
        CareDiagnosisService diagnosis = mock(CareDiagnosisService.class);
        doThrow(new IllegalStateException("upstream down"))
                .when(diagnosis).regenerate(eq(fixture.productId()), eq(SensorPeriod.THIRTY_DAYS), any());
        CareDiagnosisWorker worker = new CareDiagnosisWorker(jobs, diagnosis);
        for (int attempt = 1; attempt <= 3; attempt++) {
            worker.runNext();
            assertThat(countReadings()).isEqualTo(1);
            assertThat(value("attempt_count", Integer.class)).isEqualTo(attempt);
            assertThat(jobs.claimNext(300)).isEmpty();
            if (attempt < 3) {
                makeAvailable();
            }
        }
        assertThat(value("status", String.class)).isEqualTo("FAILED");
        jobs.request(fixture.productId(), SensorPeriod.THIRTY_DAYS, measuredAt.plusHours(1));
        assertThat(value("status", String.class)).isEqualTo("FAILED");
        assertThat(countReadings()).isEqualTo(1);
    }

    @Test
    void expiredLeaseCanBeReclaimedAndOldWorkerCannotCompleteNewClaim() {
        jobs.request(fixture.productId(), SensorPeriod.THIRTY_DAYS, measuredAt);
        ClaimedDiagnosisJob first = jobs.claimNext(300).orElseThrow();
        jdbc.update("UPDATE care_diagnosis_jobs SET lease_until = TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP(6)) WHERE product_id = ?", fixture.productId());
        ClaimedDiagnosisJob retry = jobs.claimNext(300).orElseThrow();
        assertThat(retry.attemptCount()).isEqualTo(2);
        assertThat(retry.leaseToken()).isNotEqualTo(first.leaseToken());
        assertThat(jobs.complete(first)).isFalse();
        assertThat(jobs.complete(retry)).isTrue();
    }

    private SensorReadingBatchResponse upload(Long sequence) {
        return sensor.syncBatch(fixture.userId(), fixture.deviceId(), new SensorReadingBatchRequest(List.of(
                new SensorReadingItem(sequence, new BigDecimal("20.00"), new BigDecimal("50.00"),
                        null, 0, false, measuredAt))));
    }

    private int countReadings() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM sensor_readings WHERE device_id = ?", Integer.class, fixture.deviceId());
    }

    private <T> T value(String column, Class<T> type) {
        return jdbc.queryForObject("SELECT " + column + " FROM care_diagnosis_jobs WHERE product_id = ? AND analysis_period = '30D'", type,
                fixture.productId());
    }

    private void makeAvailable() {
        jdbc.update("UPDATE care_diagnosis_jobs SET available_at = TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP(6)) WHERE product_id = ?", fixture.productId());
    }

    private record Fixture(Long userId, Long productId, Long deviceId, Long linkId) { }
}
