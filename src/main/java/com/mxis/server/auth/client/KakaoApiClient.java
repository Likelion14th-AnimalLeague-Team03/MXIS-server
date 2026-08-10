package com.mxis.server.auth.client;

import com.mxis.server.common.exception.BusinessException;
import com.mxis.server.common.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 클라이언트(모바일 앱)가 카카오 SDK로 로그인해 발급받은 access token을 검증하고
 * 카카오 사용자 정보를 조회한다. 액세스 토큰만으로 호출 가능한 "사용자 정보 가져오기" API만
 * 사용하므로, 백엔드에는 카카오 REST API 키/시크릿 설정이 필요 없다.
 */
@Component
public class KakaoApiClient {

    private final RestClient restClient;

    public KakaoApiClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl("https://kapi.kakao.com")
                .build();
    }

    public KakaoUserInfo getUserInfo(String kakaoAccessToken) {
        KakaoUserResponse response;
        try {
            response = restClient.get()
                    .uri("/v2/user/me")
                    .header("Authorization", "Bearer " + kakaoAccessToken)
                    .retrieve()
                    .body(KakaoUserResponse.class);
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.KAKAO_AUTH_FAILED, "카카오 access token 검증에 실패했습니다.");
        }

        if (response == null || response.id() == null) {
            throw new BusinessException(ErrorCode.KAKAO_AUTH_FAILED, "카카오 사용자 정보를 가져올 수 없습니다.");
        }

        String email = response.kakaoAccount() != null ? response.kakaoAccount().email() : null;
        String nickname = response.kakaoAccount() != null && response.kakaoAccount().profile() != null
                ? response.kakaoAccount().profile().nickname()
                : null;

        return new KakaoUserInfo(response.id(), email, nickname);
    }
}
