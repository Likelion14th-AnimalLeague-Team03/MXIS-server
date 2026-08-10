package com.mxis.server.product.controller;

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
import com.mxis.server.common.enums.ProductDeviceRole;
import com.mxis.server.common.exception.BusinessException;
import com.mxis.server.common.exception.ErrorCode;
import com.mxis.server.common.security.JwtAuthenticationFilter;
import com.mxis.server.common.security.JwtTokenProvider;
import com.mxis.server.config.SecurityConfig;
import com.mxis.server.product.dto.ProductDeviceLinkRequest;
import com.mxis.server.product.dto.ProductDeviceResponse;
import com.mxis.server.product.service.ProductDeviceService;
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

@WebMvcTest(ProductDeviceController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class ProductDeviceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private ProductDeviceService productDeviceService;

    private String accessToken;

    @BeforeEach
    void setUp() {
        accessToken = jwtTokenProvider.createAccessToken(1L, "user@mxis.com");
    }

    private ProductDeviceResponse sampleLink() {
        return new ProductDeviceResponse(30L, 10L, "SN-001", "내 가방 참",
                ProductDeviceRole.PRIMARY_SENSOR, LocalDateTime.now(), null);
    }

    @Test
    void link_success_returns201() throws Exception {
        ProductDeviceLinkRequest request = new ProductDeviceLinkRequest(10L, ProductDeviceRole.PRIMARY_SENSOR);
        given(productDeviceService.link(eq(1L), eq(20L), any(ProductDeviceLinkRequest.class)))
                .willReturn(sampleLink());

        mockMvc.perform(post("/api/v1/products/20/devices")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.deviceId", is(10)))
                .andExpect(jsonPath("$.data.role", is("PRIMARY_SENSOR")));
    }

    @Test
    void link_missingDeviceId_returns400() throws Exception {
        String body = "{\"role\": \"PRIMARY_SENSOR\"}";

        mockMvc.perform(post("/api/v1/products/20/devices")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_INPUT")));
    }

    @Test
    void link_alreadyLinked_returns409() throws Exception {
        ProductDeviceLinkRequest request = new ProductDeviceLinkRequest(10L, null);
        given(productDeviceService.link(eq(1L), eq(20L), any(ProductDeviceLinkRequest.class)))
                .willThrow(new BusinessException(ErrorCode.DEVICE_ALREADY_LINKED));

        mockMvc.perform(post("/api/v1/products/20/devices")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("DEVICE_ALREADY_LINKED")));
    }

    @Test
    void getLinkedDevices_success_returnsList() throws Exception {
        given(productDeviceService.getLinkedDevices(eq(1L), eq(20L))).willReturn(List.of(sampleLink()));

        mockMvc.perform(get("/api/v1/products/20/devices")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id", is(30)));
    }

    @Test
    void promoteToPrimary_success_returns200() throws Exception {
        given(productDeviceService.promoteToPrimary(eq(1L), eq(20L), eq(10L))).willReturn(sampleLink());

        mockMvc.perform(patch("/api/v1/products/20/devices/10")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role", is("PRIMARY_SENSOR")));
    }

    @Test
    void unlink_success_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/products/20/devices/10")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void unlink_notFound_returns404() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.PRODUCT_DEVICE_LINK_NOT_FOUND))
                .when(productDeviceService).unlink(eq(1L), eq(20L), eq(99L));

        mockMvc.perform(delete("/api/v1/products/20/devices/99")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("PRODUCT_DEVICE_LINK_NOT_FOUND")));
    }
}
