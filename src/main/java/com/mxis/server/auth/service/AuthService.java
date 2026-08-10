package com.mxis.server.auth.service;

import com.mxis.server.auth.dto.LoginRequest;
import com.mxis.server.auth.dto.RefreshRequest;
import com.mxis.server.auth.dto.SignupRequest;
import com.mxis.server.auth.dto.SignupResponse;
import com.mxis.server.auth.dto.TokenResponse;
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
