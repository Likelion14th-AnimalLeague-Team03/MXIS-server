package com.mxis.server.device.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mxis.server.care.dto.ScreenProductSummary;
import com.mxis.server.common.enums.DeviceConnectionStatus;
import com.mxis.server.common.security.JwtAuthenticationFilter;
import com.mxis.server.common.security.JwtTokenProvider;
import com.mxis.server.config.SecurityConfig;
import com.mxis.server.device.dto.management.DeviceManagementSummaryResponse;
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
}
