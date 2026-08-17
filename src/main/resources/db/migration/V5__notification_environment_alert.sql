-- erd.md 2026-08-17 최신화분 반영: 온습도 등 보관 환경이 안정 범위를 벗어났을 때 알림 여부.
-- 마이페이지 알림 설정 화면의 "환경 변화 감지" 토글에 대응한다.

ALTER TABLE notification_settings
    ADD COLUMN environment_alert_enabled BOOLEAN NOT NULL DEFAULT TRUE COMMENT '환경 변화(온습도 이상) 알림' AFTER marketing_enabled;
