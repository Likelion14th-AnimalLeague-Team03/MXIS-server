package com.mxis.server.device.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mxis.server.care.dto.ScreenProductSummary;
import com.mxis.server.common.enums.DeviceConnectionStatus;
import com.mxis.server.common.enums.ProductDeviceRole;
import com.mxis.server.common.security.JwtAuthenticationFilter;
import com.mxis.server.common.security.JwtTokenProvider;
import com.mxis.server.config.SecurityConfig;
import com.mxis.server.device.dto.management.DeviceManagementSummaryResponse;
import com.mxis.server.device.dto.management.ProductDeviceManagementSummaryResponse;
import com.mxis.server.device.service.DeviceManagementService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DeviceManagementController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class DeviceManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private DeviceManagementService deviceManagementService;

    private String accessToken;

    @BeforeEach
    void setUp() {
        accessToken = jwtTokenProvider.createAccessToken(1L, "user@mxis.com");
    }

    @Test
    void getSummary_success_returnsDeviceManagementFields() throws Exception {
        given(deviceManagementService.getSummary(eq(1L))).willReturn(new DeviceManagementSummaryResponse(
                List.of(new DeviceManagementSummaryResponse.ProductImage(
                        20L,
                        "https://example.com/product.png")),
                new ScreenProductSummary(
                        20L,
                        "https://example.com/product.png",
                        "Stark Backpack",
                        "canvas",
                        "Visetos Canvas",
                        "Cognac",
                        "MMKAAVE01CO001",
                        null),
                45,
                new DeviceManagementSummaryResponse.PrimaryDevice(
                        10L,
                        "MXIS-001",
                        "https://example.com/device.png",
                        DeviceConnectionStatus.CONNECTED,
                        82,
                        LocalDateTime.of(2026, 8, 17, 12, 0)),
                new DeviceManagementSummaryResponse.CurrentEnvironment(
                        new BigDecimal("24.2"),
                        new BigDecimal("47.8"),
                        LocalDateTime.of(2026, 8, 17, 12, 5))));

        mockMvc.perform(get("/api/v1/device-management/summary")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.products[0].productImageUrl", is("https://example.com/product.png")))
                .andExpect(jsonPath("$.data.primaryProduct.materialDisplayName", is("Visetos Canvas")))
                .andExpect(jsonPath("$.data.totalOutingCount", is(45)))
                .andExpect(jsonPath("$.data.primaryDevice.deviceImageUrl", is("https://example.com/device.png")))
                .andExpect(jsonPath("$.data.currentEnvironment.temperature", is(24.2)));
    }

    @Test
    void getSummary_requiresAuth_returns401WithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/device-management/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getProductSummary_success_returnsSelectedProductDeviceManagementFields() throws Exception {
        given(deviceManagementService.getProductSummary(eq(1L), eq(20L)))
                .willReturn(new ProductDeviceManagementSummaryResponse(
                        new ProductDeviceManagementSummaryResponse.ProductSummary(
                                20L,
                                "https://example.com/product.png",
                                "Stark Backpack",
                                "canvas",
                                "Visetos Canvas",
                                "Cognac",
                                "MMKAAVE01CO001",
                                "MXIS-DPP-001",
                                false),
                        new ProductDeviceManagementSummaryResponse.CurrentEnvironment(
                                new BigDecimal("23.5"),
                                new BigDecimal("48.0"),
                                LocalDateTime.of(2026, 8, 19, 14, 20)),
                        45,
                        new ProductDeviceManagementSummaryResponse.ConnectedDevice(
                                10L,
                                "MXIS-001",
                                "내 MXIS",
                                "https://example.com/device.png",
                                ProductDeviceRole.PRIMARY_SENSOR,
                                DeviceConnectionStatus.CONNECTED,
                                82,
                                LocalDateTime.of(2026, 8, 19, 14, 21)),
                        List.of(new ProductDeviceManagementSummaryResponse.ConnectedDevice(
                                10L,
                                "MXIS-001",
                                "내 MXIS",
                                "https://example.com/device.png",
                                ProductDeviceRole.PRIMARY_SENSOR,
                                DeviceConnectionStatus.CONNECTED,
                                82,
                                LocalDateTime.of(2026, 8, 19, 14, 21)))));

        mockMvc.perform(get("/api/v1/device-management/products/20/summary")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.product.productId", is(20)))
                .andExpect(jsonPath("$.data.product.materialId", is("canvas")))
                .andExpect(jsonPath("$.data.product.materialDisplayName", is("Visetos Canvas")))
                .andExpect(jsonPath("$.data.product.modelCode", is("MMKAAVE01CO001")))
                .andExpect(jsonPath("$.data.product.dppCode", is("MXIS-DPP-001")))
                .andExpect(jsonPath("$.data.product.isPrimary", is(false)))
                .andExpect(jsonPath("$.data.currentEnvironment.temperature", is(23.5)))
                .andExpect(jsonPath("$.data.currentEnvironment.humidity", is(48.0)))
                .andExpect(jsonPath("$.data.totalOutingCount", is(45)))
                .andExpect(jsonPath("$.data.primaryDevice.deviceName", is("내 MXIS")))
                .andExpect(jsonPath("$.data.primaryDevice.connectionStatus", is("CONNECTED")))
                .andExpect(jsonPath("$.data.connectedDevices[0].role", is("PRIMARY_SENSOR")))
                .andExpect(jsonPath("$.data.connectedDevices[0].batteryLevel", is(82)));
    }
}
