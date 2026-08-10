package com.mxis.server.user.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mxis.server.common.enums.AuthProvider;
import com.mxis.server.common.enums.ConsentAction;
import com.mxis.server.common.enums.ConsentType;
import com.mxis.server.common.security.JwtAuthenticationFilter;
import com.mxis.server.common.security.JwtTokenProvider;
import com.mxis.server.config.SecurityConfig;
import com.mxis.server.user.dto.ConsentItem;
import com.mxis.server.user.dto.ConsentStatusResponse;
import com.mxis.server.user.dto.ConsentUpdateRequest;
import com.mxis.server.user.dto.NotificationSettingResponse;
import com.mxis.server.user.dto.NotificationSettingUpdateRequest;
import com.mxis.server.user.dto.UserResponse;
import com.mxis.server.user.service.ConsentService;
import com.mxis.server.user.service.NotificationSettingService;
import com.mxis.server.user.service.UserService;
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

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserService userService;

    @MockBean
    private ConsentService consentService;

    @MockBean
    private NotificationSettingService notificationSettingService;

    private String accessToken;

    @BeforeEach
    void setUp() {
        accessToken = jwtTokenProvider.createAccessToken(1L, "user@mxis.com");
    }

    @Test
    void getMe_success_returnsUser() throws Exception {
        given(userService.getMe(eq(1L))).willReturn(new UserResponse(
                1L, "user@mxis.com", "홍길동", "01012345678", AuthProvider.LOCAL, LocalDateTime.now()));

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", is(1)))
                .andExpect(jsonPath("$.data.email", is("user@mxis.com")));
    }

    @Test
    void getMe_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("UNAUTHENTICATED")));
    }

    @Test
    void getConsents_success_returnsList() throws Exception {
        given(consentService.getStatus(eq(1L))).willReturn(List.of(
                new ConsentStatusResponse(ConsentType.TERMS_OF_SERVICE, true, "1.0", LocalDateTime.now())));

        mockMvc.perform(get("/api/v1/users/me/consents")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].consentType", is("TERMS_OF_SERVICE")))
                .andExpect(jsonPath("$.data[0].agreed", is(true)));
    }

    @Test
    void updateConsents_success_returnsUpdatedList() throws Exception {
        ConsentUpdateRequest request = new ConsentUpdateRequest(
                List.of(new ConsentItem(ConsentType.MARKETING, ConsentAction.AGREED, "1.0")));
        given(consentService.updateConsents(eq(1L), any(ConsentUpdateRequest.class))).willReturn(List.of(
                new ConsentStatusResponse(ConsentType.MARKETING, true, "1.0", LocalDateTime.now())));

        mockMvc.perform(post("/api/v1/users/me/consents")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].consentType", is("MARKETING")));
    }

    @Test
    void updateConsents_emptyList_returns400() throws Exception {
        String body = "{\"consents\": []}";

        mockMvc.perform(post("/api/v1/users/me/consents")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_INPUT")));
    }

    @Test
    void getNotificationSettings_success() throws Exception {
        given(notificationSettingService.get(eq(1L)))
                .willReturn(new NotificationSettingResponse(true, true, true, false, true));

        mockMvc.perform(get("/api/v1/users/me/notification-settings")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.marketingEnabled", is(false)));
    }

    @Test
    void updateNotificationSettings_success() throws Exception {
        NotificationSettingUpdateRequest request =
                new NotificationSettingUpdateRequest(false, null, null, null, null, null);
        given(notificationSettingService.update(eq(1L), any(NotificationSettingUpdateRequest.class)))
                .willReturn(new NotificationSettingResponse(false, true, true, false, true));

        mockMvc.perform(patch("/api/v1/users/me/notification-settings")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.careTimingEnabled", is(false)));
    }
}
