package com.mxis.server.home.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mxis.server.care.entity.CareReport;
import com.mxis.server.care.repository.CareReportRepository;
import com.mxis.server.care.repository.CareSuggestionRepository;
import com.mxis.server.device.entity.Device;
import com.mxis.server.home.dto.HomeResponse;
import com.mxis.server.product.entity.Product;
import com.mxis.server.product.entity.ProductDevice;
import com.mxis.server.product.repository.ProductDeviceRepository;
import com.mxis.server.product.repository.ProductRepository;
import com.mxis.server.reservation.repository.ReservationRepository;
import com.mxis.server.user.entity.User;
import com.mxis.server.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class HomeServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final ProductDeviceRepository productDeviceRepository = mock(ProductDeviceRepository.class);
    private final CareReportRepository careReportRepository = mock(CareReportRepository.class);
    private final CareSuggestionRepository careSuggestionRepository = mock(CareSuggestionRepository.class);
    private final ReservationRepository reservationRepository = mock(ReservationRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-14T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final LocalDateTime now = LocalDateTime.now(clock);
    private final HomeService service = new HomeService(
            userRepository, productRepository, productDeviceRepository,
            careReportRepository, careSuggestionRepository, reservationRepository, clock);

    private User user;
    private Product product;

    @BeforeEach
    void setUp() {
        user = User.createLocal("user@mxis.com", "encoded", "홍길동", "01000000000");
        ReflectionTestUtils.setField(user, "id", 1L);
        product = new Product(user, null, "가방", null, "leather", "가죽", null, "브라운",
                "https://img", null, now.toLocalDate().minusDays(182));

        when(userRepository.findActiveById(anyLong())).thenReturn(Optional.of(user));
        when(productRepository.findActiveById(anyLong())).thenReturn(Optional.of(product));
        when(reservationRepository
                .findFirstByProductIdAndStatusAndReservedDateGreaterThanEqualOrderByReservedDateAscReservedTimeAsc(
                        anyLong(), any(), any()))
                .thenReturn(Optional.empty());
        when(careSuggestionRepository.findLatestActiveByProductId(anyLong(), any())).thenReturn(Optional.empty());
    }

    @Test
    void noReport_meansCollecting() {
        when(careReportRepository.findFirstByProductIdOrderByCreatedAtDesc(anyLong())).thenReturn(Optional.empty());
        when(productDeviceRepository.findActivePrimaryByProductId(anyLong())).thenReturn(Optional.empty());

        HomeResponse response = service.getHome(1L, 1L);

        assertThat(response.productState()).isEqualTo(HomeResponse.ProductState.COLLECTING);
        assertThat(response.score()).isNull();
        assertThat(response.charmNeedsReconnect()).isTrue();
        assertThat(response.daysTogether()).isEqualTo(182);
    }

    @Test
    void reportExistsButDeviceStale_meansNeedsUpdate() {
        CareReport report = mock(CareReport.class);
        when(report.getDataStatus()).thenReturn("SUFFICIENT");
        when(report.getPeriodEnd()).thenReturn(now);
        when(careReportRepository.findFirstByProductIdOrderByCreatedAtDesc(anyLong())).thenReturn(Optional.of(report));

        Device device = new Device(user, "SN-1", "참", "AA:BB", "1.0", null);
        device.markSynced(now.minusDays(10));
        ProductDevice productDevice = mock(ProductDevice.class);
        when(productDevice.getDevice()).thenReturn(device);
        when(productDeviceRepository.findActivePrimaryByProductId(anyLong())).thenReturn(Optional.of(productDevice));

        HomeResponse response = service.getHome(1L, 1L);

        assertThat(response.productState()).isEqualTo(HomeResponse.ProductState.NEEDS_UPDATE);
        assertThat(response.score()).isNull();
    }

    @Test
    void reportFreshAndDeviceSynced_meansNormalWithScore() {
        CareReport report = mock(CareReport.class);
        when(report.getConditionScore()).thenReturn(92);
        when(report.getSummaryText()).thenReturn("AI가 저장한 요약입니다.");
        when(report.getDataStatus()).thenReturn("SUFFICIENT");
        when(report.getPeriodEnd()).thenReturn(now);
        when(careReportRepository.findFirstByProductIdOrderByCreatedAtDesc(anyLong())).thenReturn(Optional.of(report));

        Device device = new Device(user, "SN-1", "참", "AA:BB", "1.0", null);
        device.markSynced(now);
        ProductDevice productDevice = mock(ProductDevice.class);
        when(productDevice.getDevice()).thenReturn(device);
        when(productDeviceRepository.findActivePrimaryByProductId(anyLong())).thenReturn(Optional.of(productDevice));

        HomeResponse response = service.getHome(1L, 1L);

        assertThat(response.productState()).isEqualTo(HomeResponse.ProductState.NORMAL);
        assertThat(response.score()).isEqualTo(92);
        assertThat(response.charmNeedsReconnect()).isFalse();
        assertThat(response.headline()).isEqualTo("AI가 저장한 요약입니다.");
    }

    @Test
    void insufficientReportNeverAppearsNormalOrReceivesInventedScore() {
        CareReport report = mock(CareReport.class);
        when(report.getDataStatus()).thenReturn("INSUFFICIENT_DATA");
        when(report.getSummaryText()).thenReturn("데이터가 더 필요합니다.");
        when(careReportRepository.findFirstByProductIdOrderByCreatedAtDesc(anyLong())).thenReturn(Optional.of(report));
        when(productDeviceRepository.findActivePrimaryByProductId(anyLong())).thenReturn(Optional.empty());

        HomeResponse response = service.getHome(1L, 1L);

        assertThat(response.productState()).isEqualTo(HomeResponse.ProductState.COLLECTING);
        assertThat(response.score()).isNull();
        assertThat(response.headline()).isEqualTo("데이터가 더 필요합니다.");
    }
    @Test
    void oldReportStaysNeedsUpdateAfterDuplicateUploadRefreshesDeviceTimestamp() {
        CareReport report = mock(CareReport.class);
        when(report.getDataStatus()).thenReturn("SUFFICIENT");
        when(report.getPeriodEnd()).thenReturn(now.minusDays(7));
        when(report.getConditionScore()).thenReturn(95);
        when(careReportRepository.findFirstByProductIdOrderByCreatedAtDesc(anyLong())).thenReturn(Optional.of(report));
        Device device = new Device(user, "SN-1", "참", "AA:BB", "1.0", null);
        device.markSynced(now);
        ProductDevice productDevice = mock(ProductDevice.class);
        when(productDevice.getDevice()).thenReturn(device);
        when(productDeviceRepository.findActivePrimaryByProductId(anyLong())).thenReturn(Optional.of(productDevice));

        HomeResponse response = service.getHome(1L, 1L);

        assertThat(response.productState()).isEqualTo(HomeResponse.ProductState.NEEDS_UPDATE);
        assertThat(response.score()).isNull();
    }

}
