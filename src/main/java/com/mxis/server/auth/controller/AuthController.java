package com.mxis.server.auth.controller;

import com.mxis.server.auth.dto.KakaoLoginRequest;
import com.mxis.server.auth.dto.LoginRequest;
import com.mxis.server.auth.dto.RefreshRequest;
import com.mxis.server.auth.dto.SignupRequest;
import com.mxis.server.auth.dto.SignupResponse;
import com.mxis.server.auth.dto.TokenResponse;
import com.mxis.server.auth.service.AuthService;
import com.mxis.server.common.response.ApiResponse;
import com.mxis.server.common.security.UserPrincipal;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // 이메일/비밀번호 자체 회원가입 화면은 아직 기획 확정 전이라 비활성화 (2026-08-17).
    // 로그인은 카카오 온보딩(신규는 카카오 정보로 가입, 기존 이메일과 겹치면 계정 연결) + 기존 MCM 계정 로그인으로만 진입.
    // 재활성화 시 이 주석만 풀면 됨 — AuthService.signup/SignupRequest/SignupResponse는 그대로 유지.
    // @SecurityRequirements
    // @PostMapping("/signup")
    // @ResponseStatus(HttpStatus.CREATED)
    // public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
    //     return ApiResponse.ok(authService.signup(request));
    // }

    @SecurityRequirements
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @SecurityRequirements
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request));
    }

    // 카카오 로그인도 일단 비활성화, 일반 이메일/비밀번호 로그인만 구현 (2026-08-17).
    // 재활성화 시 이 주석만 풀면 됨 — AuthService.kakaoLogin/KakaoLoginRequest는 그대로 유지.
    // @SecurityRequirements
    // @PostMapping("/kakao/login")
    // public ApiResponse<TokenResponse> kakaoLogin(@Valid @RequestBody KakaoLoginRequest request) {
    //     return ApiResponse.ok(authService.kakaoLogin(request));
    // }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal UserPrincipal principal) {
        authService.logout(principal);
        return ApiResponse.ok();
    }
}
