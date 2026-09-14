package com.mxis.server.sensor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.mxis.server.care.service.CareDiagnosisJobService;
import com.mxis.server.common.exception.BusinessException;
import com.mxis.server.common.exception.ErrorCode;
import com.mxis.server.device.entity.Device;
import com.mxis.server.notification.service.NotificationService;
import com.mxis.server.product.entity.Product;
import com.mxis.server.product.entity.ProductDevice;
import com.mxis.server.product.repository.ProductDeviceRepository;
import com.mxis.server.product.service.ProductDeviceMutationLock;
import com.mxis.server.sensor.dto.SensorReadingBatchRequest;
import com.mxis.server.sensor.dto.SensorReadingItem;
import com.mxis.server.sensor.entity.SensorReading;
import com.mxis.server.sensor.repository.SensorReadingRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SensorReadingServiceTest {
    private final SensorReadingRepository readings = mock(SensorReadingRepository.class);
    private final ProductDeviceRepository links = mock(ProductDeviceRepository.class);
    private final ProductDeviceMutationLock locks = mock(ProductDeviceMutationLock.class);
    private final CareDiagnosisJobService jobs = mock(CareDiagnosisJobService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-14T12:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final SensorReadingService service = new SensorReadingService(readings, links, locks, jobs, notifications, clock);
    private final Device device = mock(Device.class);
    private final LocalDateTime measured = LocalDateTime.of(2026, 9, 12, 10, 0);
    private Product product;
    private ProductDevice link;

    @BeforeEach
    void setUp() {
        when(locks.lockOwnedDevice(1L, 2L)).thenReturn(device);
        product = product(10L);
        link = link(product, measured.minusDays(1), null);
        when(links.findHistoryByDeviceId(2L)).thenReturn(List.of(link));
        when(readings.findExistingReadings(eq(2L), any())).thenReturn(List.of());
    }

    @Test
    void duplicateNewSequenceIsSavedOnceAndAllRepeatedRowsAreCounted() {
        var response = service.syncBatch(1L, 2L, batch(item(7L, measured, "20.0"), item(7L, measured, "20.00")));
        assertThat(response.receivedCount()).isEqualTo(2);
        assertThat(response.savedCount()).isEqualTo(1);
        assertThat(response.duplicateCount()).isEqualTo(1);
        verify(jobs).requestAfterSensorChange(eq(10L), any());
        var order = inOrder(locks, readings);
        order.verify(locks).lockOwner(1L);
        order.verify(locks).lockOwnedDevice(1L, 2L);
        order.verify(readings).findExistingReadings(eq(2L), any());
    }

    @Test
    void existingReplayAndBatchDuplicatesCountAllInputRowsWithoutRequeue() {
        SensorReading stored = new SensorReading(product, device, link, 7L, new BigDecimal("20.00"),
                new BigDecimal("50.00"), null, 0, false, measured);
        when(readings.findExistingReadings(eq(2L), any())).thenReturn(List.of(stored));
        var response = service.syncBatch(1L, 2L, batch(item(7L, measured, "20"), item(7L, measured, "20.0")));
        assertThat(response.savedCount()).isZero();
        assertThat(response.duplicateCount()).isEqualTo(2);
        verifyNoInteractions(jobs, notifications);
    }

    @Test
    void conflictingPayloadInBatchIsRejectedBeforeSaving() {
        assertThatThrownBy(() -> service.syncBatch(1L, 2L,
                batch(item(7L, measured, "20"), item(7L, measured, "21"))))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
        verifyNoInteractions(readings, jobs, notifications);
    }

    @Test
    void conflictingPreviouslyStoredSequenceIsRejected() {
        SensorReading stored = new SensorReading(product, device, link, 7L, new BigDecimal("21"),
                new BigDecimal("50"), null, 0, false, measured);
        when(readings.findExistingReadings(eq(2L), any())).thenReturn(List.of(stored));
        assertThatThrownBy(() -> service.syncBatch(1L, 2L, batch(item(7L, measured, "20"))))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
        verify(readings, never()).saveAll(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void delayedUploadUsesHistoricalProductAndExactDetachBoundaryUsesNextLink() {
        LocalDateTime boundary = measured.plusHours(1);
        Product newer = product(20L);
        ProductDevice historical = link(product, measured.minusDays(1), boundary);
        ProductDevice current = link(newer, boundary, null);
        when(links.findHistoryByDeviceId(2L)).thenReturn(List.of(historical, current));
        service.syncBatch(1L, 2L, batch(item(7L, measured, "20"), item(8L, boundary, "20")));
        ArgumentCaptor<List<SensorReading>> saved = ArgumentCaptor.forClass(List.class);
        verify(readings).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(reading -> reading.getProduct().getId()).containsExactly(10L, 20L);
        verify(jobs).requestAfterSensorChange(eq(10L), any());
        verify(jobs).requestAfterSensorChange(eq(20L), any());
    }

    @Test
    void gapInConnectionHistoryRejectsWholeBatch() {
        ProductDevice futureLink = link(product, measured.plusHours(1), null);
        when(links.findHistoryByDeviceId(2L)).thenReturn(List.of(futureLink));
        assertThatThrownBy(() -> service.syncBatch(1L, 2L, batch(item(7L, measured, "20"))))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(ErrorCode.DEVICE_NOT_LINKED_TO_PRODUCT);
        verify(readings, never()).saveAll(any());
    }

    @Test
    void overlappingConnectionHistoryIsNotArbitrarilyAssigned() {
        ProductDevice overlapping = link(product(20L), measured.minusHours(1), null);
        when(links.findHistoryByDeviceId(2L)).thenReturn(List.of(link, overlapping));
        assertThatThrownBy(() -> service.syncBatch(1L, 2L, batch(item(7L, measured, "20"))))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
        verify(readings, never()).saveAll(any());
    }

    @Test
    void historicalRecordFromDifferentOwnerIsNotAccepted() {
        when(product.isOwnedBy(1L)).thenReturn(false);
        assertThatThrownBy(() -> service.syncBatch(1L, 2L, batch(item(7L, measured, "20"))))
                .isInstanceOf(BusinessException.class).extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);
        verifyNoInteractions(jobs, notifications);
    }

    @Test
    void microsecondNormalizationMakesSubMicrosecondReplayStable() {
        var response = service.syncBatch(1L, 2L, batch(item(7L, measured.withNano(123456001), "20"),
                item(7L, measured.withNano(123456999), "20")));
        assertThat(response.savedCount()).isEqualTo(1);
        assertThat(response.duplicateCount()).isEqualTo(1);
    }

    private Product product(Long id) {
        Product result = mock(Product.class);
        when(result.getId()).thenReturn(id);
        when(result.isOwnedBy(1L)).thenReturn(true);
        return result;
    }

    private ProductDevice link(Product target, LocalDateTime from, LocalDateTime to) {
        ProductDevice result = mock(ProductDevice.class);
        when(result.getProduct()).thenReturn(target);
        when(result.getAttachedAt()).thenReturn(from);
        when(result.getDetachedAt()).thenReturn(to);
        return result;
    }

    private SensorReadingItem item(Long sequence, LocalDateTime at, String temperature) {
        return new SensorReadingItem(sequence, new BigDecimal(temperature), new BigDecimal("50"), null, 0, false, at);
    }

    private SensorReadingBatchRequest batch(SensorReadingItem... items) {
        return new SensorReadingBatchRequest(List.of(items));
    }
}
