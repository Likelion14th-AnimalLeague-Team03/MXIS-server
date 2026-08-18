package com.mxis.server.auth.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mxis.server.auth.dto.KakaoLoginRequest;
import com.mxis.server.auth.dto.LoginRequest;
import com.mxis.server.auth.dto.RefreshRequest;
import com.mxis.server.auth.dto.SignupRequest;
import com.mxis.server.auth.dto.SignupResponse;
import com.mxis.server.auth.dto.TokenResponse;
import com.mxis.server.auth.service.AuthService;
import com.mxis.server.common.enums.AuthProvider;
import com.mxis.server.common.exception.BusinessException;
import com.mxis.server.common.exception.ErrorCode;
import com.mxis.server.common.security.JwtAuthenticationFilter;
import com.mxis.server.common.security.JwtTokenProvider;
import com.mxis.server.config.SecurityConfig;
import com.mxis.server.user.dto.UserResponse;
import java.time.LocalDateTime;
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

    private static UserResponse stubUser() {
        return new UserResponse(1L, "user@mxis.com", "홍길동", "01012345678", AuthProvider.LOCAL, LocalDateTime.now());
    }

    // 회원가입 라우트가 비활성화돼 있어 아래 3개 테스트도 함께 비활성화 (AuthController.signup() 주석 참고).
    // 재활성화 시 이 테스트들도 함께 주석 해제.
    // @Test
    // void signup_success_returns201() throws Exception {
    //     SignupRequest request = new SignupRequest("user@mxis.com", "password1", "홍길동", "01012345678");
    //     given(authService.signup(any(SignupRequest.class)))
    //             .willReturn(new SignupResponse(1L, "user@mxis.com", "홍길동"));
    //
    //     mockMvc.perform(post("/api/v1/auth/signup")
    //                     .contentType(MediaType.APPLICATION_JSON)
    //                     .content(objectMapper.writeValueAsString(request)))
    //             .andExpect(status().isCreated())
    //             .andExpect(jsonPath("$.success", is(true)))
    //             .andExpect(jsonPath("$.data.userId", is(1)))
    //             .andExpect(jsonPath("$.data.email", is("user@mxis.com")));
    // }
    //
    // @Test
    // void signup_invalidEmail_returns400() throws Exception {
    //     SignupRequest request = new SignupRequest("not-an-email", "password1", "홍길동", null);
    //
    //     mockMvc.perform(post("/api/v1/auth/signup")
    //                     .contentType(MediaType.APPLICATION_JSON)
    //                     .content(objectMapper.writeValueAsString(request)))
    //             .andExpect(status().isBadRequest())
    //             .andExpect(jsonPath("$.success", is(false)))
    //             .andExpect(jsonPath("$.error.code", is("INVALID_INPUT")));
    // }
    //
    // @Test
    // void signup_emailAlreadyExists_returns409() throws Exception {
    //     SignupRequest request = new SignupRequest("dup@mxis.com", "password1", "홍길동", null);
    //     given(authService.signup(any(SignupRequest.class)))
    //             .willThrow(new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS));
    //
    //     mockMvc.perform(post("/api/v1/auth/signup")
    //                     .contentType(MediaType.APPLICATION_JSON)
    //                     .content(objectMapper.writeValueAsString(request)))
    //             .andExpect(status().isConflict())
    //             .andExpect(jsonPath("$.error.code", is("EMAIL_ALREADY_EXISTS")));
    // }

    @Test
    void login_success_returnsTokens() throws Exception {
        LoginRequest request = new LoginRequest("user@mxis.com", "password1");
        given(authService.login(any(LoginRequest.class)))
                .willReturn(new TokenResponse("access-token", "refresh-token", "Bearer", stubUser()));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", is("access-token")))
                .andExpect(jsonPath("$.data.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.data.user.email", is("user@mxis.com")));
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
                .willReturn(new TokenResponse("new-access-token", "new-refresh-token", "Bearer", stubUser()));

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
    void kakaoLogin_success_returnsTokens() throws Exception {
        KakaoLoginRequest request = new KakaoLoginRequest("kakao-access-token");
        given(authService.kakaoLogin(any(KakaoLoginRequest.class)))
                .willReturn(new TokenResponse("access-token", "refresh-token", "Bearer", stubUser()));

        mockMvc.perform(post("/api/v1/auth/kakao/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", is("access-token")))
                .andExpect(jsonPath("$.data.tokenType", is("Bearer")));
    }

    @Test
    void kakaoLogin_invalidKakaoToken_returns401() throws Exception {
        KakaoLoginRequest request = new KakaoLoginRequest("bad-token");
        given(authService.kakaoLogin(any(KakaoLoginRequest.class)))
                .willThrow(new BusinessException(ErrorCode.KAKAO_AUTH_FAILED));

        mockMvc.perform(post("/api/v1/auth/kakao/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("KAKAO_AUTH_FAILED")));
    }

    @Test
    void kakaoLogin_blankAccessToken_returns400() throws Exception {
        String body = "{\"accessToken\": \"\"}";

        mockMvc.perform(post("/api/v1/auth/kakao/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_INPUT")));
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
