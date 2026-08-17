package com.mxis.server.device.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mxis.server.common.enums.DeviceConnectionStatus;
import com.mxis.server.common.exception.BusinessException;
import com.mxis.server.common.exception.ErrorCode;
import com.mxis.server.common.security.JwtAuthenticationFilter;
import com.mxis.server.common.security.JwtTokenProvider;
import com.mxis.server.config.SecurityConfig;
import com.mxis.server.device.dto.DeviceLookupResponse;
import com.mxis.server.device.dto.DeviceRegisterRequest;
import com.mxis.server.device.dto.DeviceResponse;
import com.mxis.server.device.dto.DeviceStatusUpdateRequest;
import com.mxis.server.device.service.DeviceService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DeviceController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class DeviceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private DeviceService deviceService;

    private String accessToken;

    @BeforeEach
    void setUp() {
        accessToken = jwtTokenProvider.createAccessToken(1L, "user@mxis.com");
    }

    private DeviceResponse sampleDevice() {
        return new DeviceResponse(10L, "SN-001", "내 가방 참", "AA:BB:CC:DD:EE:FF", "1.0.0",
                "https://example.com/device.png", 80,
                DeviceConnectionStatus.CONNECTED, LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    void register_success_returns201() throws Exception {
        DeviceRegisterRequest request = new DeviceRegisterRequest(
                "SN-001", "내 가방 참", "AA:BB:CC:DD:EE:FF", "1.0.0",
                "https://example.com/device.png");
        given(deviceService.register(eq(1L), any(DeviceRegisterRequest.class))).willReturn(sampleDevice());

        mockMvc.perform(post("/api/v1/devices")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.serialNumber", is("SN-001")));
    }

    @Test
    void register_blankSerialNumber_returns400() throws Exception {
        DeviceRegisterRequest request = new DeviceRegisterRequest("", null, null, null, null);

        mockMvc.perform(post("/api/v1/devices")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_INPUT")));
    }

    @Test
    void register_alreadyRegistered_returns409() throws Exception {
        DeviceRegisterRequest request = new DeviceRegisterRequest("SN-001", null, null, null, null);
        given(deviceService.register(eq(1L), any(DeviceRegisterRequest.class)))
                .willThrow(new BusinessException(ErrorCode.DEVICE_ALREADY_REGISTERED));

        mockMvc.perform(post("/api/v1/devices")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("DEVICE_ALREADY_REGISTERED")));
    }

    @Test
    void getMyDevices_success_returnsList() throws Exception {
        given(deviceService.getMyDevices(eq(1L))).willReturn(List.of(sampleDevice()));

        mockMvc.perform(get("/api/v1/devices")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id", is(10)));
    }

    @Test
    void lookup_requiresAuth_returns401WithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/devices/lookup").param("serialNumber", "SN-001"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void lookup_success_returnsRegistrable() throws Exception {
        given(deviceService.lookup("SN-001")).willReturn(new DeviceLookupResponse("SN-001", true));

        mockMvc.perform(get("/api/v1/devices/lookup")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("serialNumber", "SN-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.registrable", is(true)));
    }

    @Test
    void getDevice_notFound_returns404() throws Exception {
        given(deviceService.getDevice(eq(1L), eq(99L)))
                .willThrow(new BusinessException(ErrorCode.DEVICE_NOT_FOUND));

        mockMvc.perform(get("/api/v1/devices/99")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("DEVICE_NOT_FOUND")));
    }

    @Test
    void updateStatus_batteryLevelOutOfRange_returns400() throws Exception {
        DeviceStatusUpdateRequest request = new DeviceStatusUpdateRequest(DeviceConnectionStatus.CONNECTED, 150);

        mockMvc.perform(patch("/api/v1/devices/10/status")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_INPUT")));
    }

    @Test
    void updateStatus_success_returnsUpdatedDevice() throws Exception {
        DeviceStatusUpdateRequest request = new DeviceStatusUpdateRequest(DeviceConnectionStatus.SYNCING, 55);
        given(deviceService.updateStatus(eq(1L), eq(10L), any(DeviceStatusUpdateRequest.class)))
                .willReturn(sampleDevice());

        mockMvc.perform(patch("/api/v1/devices/10/status")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(10)));
    }

    @Test
    void delete_success_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/devices/10")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());
    }
}
