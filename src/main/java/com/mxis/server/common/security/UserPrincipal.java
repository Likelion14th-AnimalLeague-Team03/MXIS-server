package com.mxis.server.common.security;

/**
 * 인증된 요청의 SecurityContext에 담기는 최소 정보.
 * JWT의 subject(사용자 id)만 검증하고, 상세 프로필은 필요 시 UserRepository에서 조회한다.
 */
public record UserPrincipal(Long userId, String email) {
}
