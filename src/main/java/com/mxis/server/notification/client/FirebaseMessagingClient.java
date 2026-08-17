package com.mxis.server.notification.client;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import java.io.FileInputStream;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * FCM 발송 래퍼. 자격증명(fcm.credentials-path)이 없으면 발송을 건너뛰고 로그만 남긴다 —
 * 로컬/CI 환경에서 FCM 서비스 계정 키 없이도 앱이 정상 기동해야 하기 때문이다.
 * 발송 실패는 절대 예외로 전파하지 않는다 (알림은 부가 기능이라 핵심 흐름을 깨면 안 됨).
 */
@Slf4j
@Component
public class FirebaseMessagingClient {

    private final boolean enabled;

    public FirebaseMessagingClient(@Value("${fcm.credentials-path:}") String credentialsPath) {
        if (credentialsPath == null || credentialsPath.isBlank()) {
            log.warn("fcm.credentials-path 미설정 — 푸시 발송이 비활성화된 채로 기동합니다.");
            this.enabled = false;
            return;
        }
        boolean initialized;
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(new FileInputStream(credentialsPath)))
                        .build();
                FirebaseApp.initializeApp(options);
            }
            initialized = true;
        } catch (IOException e) {
            log.error("FCM 초기화 실패 — 푸시 발송이 비활성화된 채로 기동합니다.", e);
            initialized = false;
        }
        this.enabled = initialized;
    }

    public void send(String deviceToken, String title, String body) {
        if (!enabled) {
            log.debug("[FCM 비활성] token={} title={}", deviceToken, title);
            return;
        }
        try {
            Message message = Message.builder()
                    .setToken(deviceToken)
                    .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                    .build();
            FirebaseMessaging.getInstance().send(message);
        } catch (Exception e) {
            log.error("FCM 발송 실패 token={}", deviceToken, e);
        }
    }
}
