package com.mxis.server.auth.service;

import com.mxis.server.auth.client.KakaoApiClient;
import com.mxis.server.auth.client.KakaoUserInfo;
import com.mxis.server.auth.dto.KakaoLoginRequest;
import com.mxis.server.auth.dto.LoginRequest;
import com.mxis.server.auth.dto.RefreshRequest;
import com.mxis.server.auth.dto.SignupRequest;
import com.mxis.server.auth.dto.SignupResponse;
import com.mxis.server.auth.dto.TokenResponse;
import com.mxis.server.common.enums.AuthProvider;
import com.mxis.server.common.exception.BusinessException;
import com.mxis.server.common.exception.ErrorCode;
import com.mxis.server.common.security.JwtTokenProvider;
import com.mxis.server.common.security.UserPrincipal;
import com.mxis.server.user.entity.NotificationSetting;
import com.mxis.server.user.entity.User;
import com.mxis.server.user.repository.NotificationSettingRepository;
import com.mxis.server.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final KakaoApiClient kakaoApiClient;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByEmailAndDeletedAtIsNull(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = User.createLocal(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.name(),
                request.phone());
        userRepository.save(user);

        // 회원가입 시 알림 설정 기본값 1행을 함께 생성한다 (notification_settings: user_id unique).
        notificationSettingRepository.save(new NotificationSetting(user));

        return new SignupResponse(user.getId(), user.getEmail(), user.getName());
    }

    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findActiveByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        return issueTokens(user);
    }

    /**
     * 클라이언트(앱)가 카카오 SDK로 로그인해 발급받은 access token을 그대로 넘겨받아 검증한다.
     * (provider, provider_uid) 조합으로 기존 회원을 찾고, 없으면 최초 로그인으로 간주해 자동 가입시킨다.
     */
    @Transactional
    public TokenResponse kakaoLogin(KakaoLoginRequest request) {
        KakaoUserInfo kakaoUser = kakaoApiClient.getUserInfo(request.accessToken());
        String providerUid = String.valueOf(kakaoUser.id());

        User user = userRepository.findActiveByProviderAndProviderUid(AuthProvider.KAKAO, providerUid)
                .orElseGet(() -> registerKakaoUser(kakaoUser, providerUid));

        return issueTokens(user);
    }

    private User registerKakaoUser(KakaoUserInfo kakaoUser, String providerUid) {
        // 카카오 계정에 이메일 동의를 하지 않은 사용자는 email이 없을 수 있다.
        // users.email은 NOT NULL + UNIQUE라 실제 이메일과 절대 충돌하지 않는 합성 이메일로 대체한다.
        String email = kakaoUser.email() != null ? kakaoUser.email() : "kakao_" + providerUid + "@kakao.mxis.local";

        if (userRepository.existsByEmailAndDeletedAtIsNull(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS, "이미 다른 방식으로 가입된 이메일입니다.");
        }

        String name = kakaoUser.nickname() != null ? kakaoUser.nickname() : "카카오 사용자";
        User user = User.createSocial(email, AuthProvider.KAKAO, providerUid, name);
        userRepository.save(user);

        // 회원가입 시 알림 설정 기본값 1행을 함께 생성한다 (notification_settings: user_id unique).
        notificationSettingRepository.save(new NotificationSetting(user));

        return user;
    }

    public TokenResponse refresh(RefreshRequest request) {
        if (!jwtTokenProvider.validate(request.refreshToken())) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        Claims claims = jwtTokenProvider.parseClaims(request.refreshToken());
        if (!jwtTokenProvider.isRefreshToken(claims)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        UserPrincipal principal = jwtTokenProvider.toPrincipal(claims);
        User user = userRepository.findActiveById(principal.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_TOKEN));

        return issueTokens(user);
    }

    /**
     * MVP는 서버 측 토큰 저장소(refresh token / blacklist 테이블)가 없는 완전한 Stateless JWT 구조다.
     * 따라서 로그아웃은 서버에서 토큰을 실효시키지 못하고, 클라이언트가 보관 중인 토큰을 폐기하는 것으로 처리한다.
     * (요청이 이 메서드까지 도달했다는 것 자체가 SecurityConfig에 의해 이미 유효한 access token 검증을 통과했다는 뜻이다.)
     */
    public void logout(UserPrincipal principal) {
        // no-op: 서버 측에 무효화할 상태가 없음. 향후 실제 폐기가 필요하면 blacklist 테이블 추가 필요.
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.createAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), user.getEmail());
        return TokenResponse.of(accessToken, refreshToken);
    }
}
