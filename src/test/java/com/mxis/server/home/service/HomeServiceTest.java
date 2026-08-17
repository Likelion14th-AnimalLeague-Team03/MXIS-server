package com.mxis.server.home.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mxis.server.care.entity.CareReport;
import com.mxis.server.care.repository.CareReportRepository;
import com.mxis.server.care.repository.CareSuggestionRepository;
import com.mxis.server.care.service.CareRuleEngine;
import com.mxis.server.common.enums.CareConditionGrade;
import com.mxis.server.device.entity.Device;
import com.mxis.server.home.dto.HomeResponse;
import com.mxis.server.product.entity.Product;
import com.mxis.server.product.entity.ProductDevice;
import com.mxis.server.product.repository.ProductDeviceRepository;
import com.mxis.server.product.repository.ProductRepository;
import com.mxis.server.reservation.repository.ReservationRepository;
import com.mxis.server.user.entity.User;
import com.mxis.server.user.repository.UserRepository;
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
    private final HomeService service = new HomeService(
            userRepository, productRepository, productDeviceRepository,
            careReportRepository, careSuggestionRepository, reservationRepository, new CareRuleEngine());

    private User user;
    private Product product;

    @BeforeEach
    void setUp() {
        user = User.createLocal("user@mxis.com", "encoded", "홍길동", "01000000000");
        ReflectionTestUtils.setField(user, "id", 1L);
        product = new Product(user, null, "가방", null, "leather", "가죽", null, "브라운",
                "https://img", LocalDate.now().minusDays(182));

        when(userRepository.findActiveById(anyLong())).thenReturn(Optional.of(user));
        when(productRepository.findActiveById(anyLong())).thenReturn(Optional.of(product));
        when(reservationRepository
                .findFirstByProductIdAndStatusAndReservedDateGreaterThanEqualOrderByReservedDateAscReservedTimeAsc(
                        anyLong(), any(), any()))
                .thenReturn(Optional.empty());
        when(careSuggestionRepository.findLatestActiveByProductId(anyLong())).thenReturn(Optional.empty());
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
        when(report.getConditionGrade()).thenReturn(CareConditionGrade.STABLE);
        when(careReportRepository.findFirstByProductIdOrderByCreatedAtDesc(anyLong())).thenReturn(Optional.of(report));

        Device device = new Device(user, "SN-1", "참", "AA:BB", "1.0", null);
        device.markSynced(LocalDateTime.now().minusDays(10));
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
        when(report.getConditionGrade()).thenReturn(CareConditionGrade.STABLE);
        when(careReportRepository.findFirstByProductIdOrderByCreatedAtDesc(anyLong())).thenReturn(Optional.of(report));

        Device device = new Device(user, "SN-1", "참", "AA:BB", "1.0", null);
        device.markSynced(LocalDateTime.now());
        ProductDevice productDevice = mock(ProductDevice.class);
        when(productDevice.getDevice()).thenReturn(device);
        when(productDeviceRepository.findActivePrimaryByProductId(anyLong())).thenReturn(Optional.of(productDevice));

        HomeResponse response = service.getHome(1L, 1L);

        assertThat(response.productState()).isEqualTo(HomeResponse.ProductState.NORMAL);
        assertThat(response.score()).isEqualTo(100);
        assertThat(response.charmNeedsReconnect()).isFalse();
        assertThat(response.headline()).isEqualTo("안정적인 상태입니다.");
    }

    @Test
    void toScore_mapsGradeTo25PercentSteps() {
        assertThat(HomeService.toScore(CareConditionGrade.STABLE)).isEqualTo(100);
        assertThat(HomeService.toScore(CareConditionGrade.BALANCED)).isEqualTo(75);
        assertThat(HomeService.toScore(CareConditionGrade.LIGHT_CARE)).isEqualTo(50);
        assertThat(HomeService.toScore(CareConditionGrade.EXPERT_CHECK)).isEqualTo(25);
    }
}
