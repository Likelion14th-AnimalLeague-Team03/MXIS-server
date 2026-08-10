package com.mxis.server.auth.client;

public record KakaoUserInfo(
        Long id,
        String email,
        String nickname
) {
}
