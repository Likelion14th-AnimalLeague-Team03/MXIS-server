package com.mxis.server.notification.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mxis.server.common.enums.NotificationType;
import com.mxis.server.common.security.JwtAuthenticationFilter;
import com.mxis.server.common.security.JwtTokenProvider;
import com.mxis.server.config.SecurityConfig;
import com.mxis.server.notification.dto.NotificationPageResponse;
import com.mxis.server.notification.dto.NotificationReadResult;
import com.mxis.server.notification.dto.NotificationResponse;
import com.mxis.server.notification.dto.NotificationUnreadCountResponse;
import com.mxis.server.notification.service.NotificationService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private NotificationService notificationService;

    private String accessToken;

    @BeforeEach
    void setUp() {
        accessToken = jwtTokenProvider.createAccessToken(1L, "user@mxis.com");
    }

    @Test
    void getNotifications_requiresAuth_returns401WithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getNotifications_success_returnsPagedList() throws Exception {
        NotificationResponse response = sampleNotification(false);
        given(notificationService.getNotifications(
                eq(1L), eq(NotificationType.ENVIRONMENT_ALERT), eq(true), eq(0), eq(20)))
                .willReturn(new NotificationPageResponse(List.of(response), 0, 20, 1, 1, false, 1));

        mockMvc.perform(get("/api/v1/notifications")
                        .param("type", "ENVIRONMENT_ALERT")
                        .param("unreadOnly", "true")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].type", is("ENVIRONMENT_ALERT")))
                .andExpect(jsonPath("$.data.content[0].isRead", is(false)))
                .andExpect(jsonPath("$.data.unreadCount", is(1)));
    }

    @Test
    void getNotification_success_returnsDetail() throws Exception {
        given(notificationService.getNotification(eq(1L), eq(10L))).willReturn(sampleNotification(false));

        mockMvc.perform(get("/api/v1/notifications/10")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(10)))
                .andExpect(jsonPath("$.data.relatedIds.productId", is(20)));
    }

    @Test
    void markRead_success_returnsReadResult() throws Exception {
        LocalDateTime readAt = LocalDateTime.of(2026, 8, 17, 12, 0);
        given(notificationService.markRead(eq(1L), eq(10L)))
                .willReturn(new NotificationReadResult(10L, true, readAt));

        mockMvc.perform(patch("/api/v1/notifications/10/read")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(10)))
                .andExpect(jsonPath("$.data.isRead", is(true)));
    }

    @Test
    void unreadCount_success_returnsCount() throws Exception {
        given(notificationService.unreadCount(eq(1L))).willReturn(new NotificationUnreadCountResponse(3));

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount", is(3)));
    }

    @Test
    void markAllRead_success_returnsUnreadCount() throws Exception {
        given(notificationService.markAllRead(eq(1L))).willReturn(new NotificationUnreadCountResponse(0));

        mockMvc.perform(patch("/api/v1/notifications/read-all")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount", is(0)));
    }

    private NotificationResponse sampleNotification(boolean isRead) {
        return new NotificationResponse(
                10L,
                NotificationType.ENVIRONMENT_ALERT,
                "보관 환경을 확인해주세요",
                "최근 습도가 안정 범위를 벗어난 기록이 있어요.",
                "/care/products/20/environment",
                Map.of("productId", 20L, "factor", "humidity"),
                new NotificationResponse.RelatedIds(20L, null, null, null, null),
                isRead,
                null,
                null,
                LocalDateTime.now());
    }
}
