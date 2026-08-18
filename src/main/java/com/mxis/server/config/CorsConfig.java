package com.mxis.server.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 로컬 개발 단계(크로스플랫폼 프론트, 포트 유동적)용 CORS.
 * 인증은 쿠키가 아니라 Authorization 헤더의 JWT라 credentials는 필요 없다.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 로컬 개발 전용. 휴대폰 등 같은 와이파이의 다른 기기가 노트북 LAN IP로 접속해도
        // 막히지 않도록 origin을 전부 허용한다 (JWT는 헤더로만 오가고 allowCredentials=false라
        // 와일드카드 origin이어도 자격증명 탈취 리스크 없음). 운영 배포 전 제거 대상.
        configuration.setAllowedOriginPatterns(List.of("http://*:*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
