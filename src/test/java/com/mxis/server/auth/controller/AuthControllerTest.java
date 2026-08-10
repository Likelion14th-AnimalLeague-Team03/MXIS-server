package com.mxis.server.auth.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mxis.server.auth.dto.LoginRequest;
import com.mxis.server.auth.dto.RefreshRequest;
import com.mxis.server.auth.dto.SignupRequest;
import com.mxis.server.auth.dto.SignupResponse;
import com.mxis.server.auth.dto.TokenResponse;
import com.mxis.server.auth.service.AuthService;
import com.mxis.server.common.exception.BusinessException;
import com.mxis.server.common.exception.ErrorCode;
import com.mxis.server.common.security.JwtAuthenticationFilter;
import com.mxis.server.common.security.JwtTokenProvider;
import com.mxis.server.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private AuthService authService;

    @Test
    void signup_success_returns201() throws Exception {
        SignupRequest request = new SignupRequest("user@mxis.com", "password1", "홍길동", "01012345678");
        given(authService.signup(any(SignupRequest.class)))
                .willReturn(new SignupResponse(1L, "user@mxis.com", "홍길동"));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.userId", is(1)))
                .andExpect(jsonPath("$.data.email", is("user@mxis.com")));
    }

    @Test
    void signup_invalidEmail_returns400() throws Exception {
        SignupRequest request = new SignupRequest("not-an-email", "password1", "홍길동", null);

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("INVALID_INPUT")));
    }

    @Test
    void signup_emailAlreadyExists_returns409() throws Exception {
        SignupRequest request = new SignupRequest("dup@mxis.com", "password1", "홍길동", null);
        given(authService.signup(any(SignupRequest.class)))
                .willThrow(new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("EMAIL_ALREADY_EXISTS")));
    }

    @Test
    void login_success_returnsTokens() throws Exception {
        LoginRequest request = new LoginRequest("user@mxis.com", "password1");
        given(authService.login(any(LoginRequest.class)))
                .willReturn(new TokenResponse("access-token", "refresh-token", "Bearer"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", is("access-token")))
                .andExpect(jsonPath("$.data.tokenType", is("Bearer")));
    }

    @Test
    void login_invalidCredentials_returns401() throws Exception {
        LoginRequest request = new LoginRequest("user@mxis.com", "wrong-password");
        given(authService.login(any(LoginRequest.class)))
                .willThrow(new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("INVALID_CREDENTIALS")));
    }

    @Test
    void refresh_success_returnsNewTokens() throws Exception {
        RefreshRequest request = new RefreshRequest("some-refresh-token");
        given(authService.refresh(any(RefreshRequest.class)))
                .willReturn(new TokenResponse("new-access-token", "new-refresh-token", "Bearer"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", is("new-access-token")));
    }

    @Test
    void refresh_invalidToken_returns401() throws Exception {
        RefreshRequest request = new RefreshRequest("garbage");
        given(authService.refresh(any(RefreshRequest.class)))
                .willThrow(new BusinessException(ErrorCode.INVALID_TOKEN));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("INVALID_TOKEN")));
    }

    @Test
    void logout_withValidToken_returns200() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken(1L, "user@mxis.com");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        verify(authService).logout(any());
    }

    @Test
    void logout_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("UNAUTHENTICATED")));
    }
}
