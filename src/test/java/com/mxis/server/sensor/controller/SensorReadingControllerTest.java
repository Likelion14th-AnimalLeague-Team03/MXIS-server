package com.mxis.server.sensor.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mxis.server.common.exception.BusinessException;
import com.mxis.server.common.exception.ErrorCode;
import com.mxis.server.common.security.JwtAuthenticationFilter;
import com.mxis.server.common.security.JwtTokenProvider;
import com.mxis.server.config.SecurityConfig;
import com.mxis.server.sensor.dto.SensorReadingBatchRequest;
import com.mxis.server.sensor.dto.SensorReadingBatchResponse;
import com.mxis.server.sensor.dto.SensorReadingItem;
import java.math.BigDecimal;
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
import com.mxis.server.sensor.service.SensorReadingService;

@WebMvcTest(SensorReadingController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class SensorReadingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private SensorReadingService sensorReadingService;

    private String accessToken;

    @BeforeEach
    void setUp() {
        accessToken = jwtTokenProvider.createAccessToken(1L, "user@mxis.com");
    }

    @Test
    void syncBatch_success_returns201() throws Exception {
        SensorReadingBatchRequest request = new SensorReadingBatchRequest(List.of(
                new SensorReadingItem(1L, new BigDecimal("22.5"), new BigDecimal("45.0"),
                        new BigDecimal("0.3"), false, LocalDateTime.now())));
        given(sensorReadingService.syncBatch(eq(1L), eq(10L), any(SensorReadingBatchRequest.class)))
                .willReturn(new SensorReadingBatchResponse(1, 1, 0, LocalDateTime.now()));

        mockMvc.perform(post("/api/v1/devices/10/sensor-readings/batch")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.receivedCount", is(1)))
                .andExpect(jsonPath("$.data.savedCount", is(1)));
    }

    @Test
    void syncBatch_emptyReadings_returns400() throws Exception {
        String body = "{\"readings\": []}";

        mockMvc.perform(post("/api/v1/devices/10/sensor-readings/batch")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_INPUT")));
    }

    @Test
    void syncBatch_deviceNotLinked_returns409() throws Exception {
        SensorReadingBatchRequest request = new SensorReadingBatchRequest(List.of(
                new SensorReadingItem(1L, null, null, null, false, LocalDateTime.now())));
        given(sensorReadingService.syncBatch(eq(1L), eq(10L), any(SensorReadingBatchRequest.class)))
                .willThrow(new BusinessException(ErrorCode.DEVICE_NOT_LINKED_TO_PRODUCT));

        mockMvc.perform(post("/api/v1/devices/10/sensor-readings/batch")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("DEVICE_NOT_LINKED_TO_PRODUCT")));
    }

    @Test
    void syncBatch_withoutToken_returns401() throws Exception {
        SensorReadingBatchRequest request = new SensorReadingBatchRequest(List.of(
                new SensorReadingItem(1L, null, null, null, false, LocalDateTime.now())));

        mockMvc.perform(post("/api/v1/devices/10/sensor-readings/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
