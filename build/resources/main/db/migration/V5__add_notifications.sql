CREATE TABLE notifications (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '알림 고유 번호',
    user_id             BIGINT NOT NULL COMMENT '알림 수신 사용자 ID',
    product_id          BIGINT NULL COMMENT '관련 제품 ID',
    device_id           BIGINT NULL COMMENT '관련 기기 ID',
    reservation_id      BIGINT NULL COMMENT '관련 예약 ID',
    care_report_id      BIGINT NULL COMMENT '관련 케어 리포트 ID',
    care_suggestion_id  BIGINT NULL COMMENT '관련 케어 제안 ID',
    notification_type   VARCHAR(40) NOT NULL COMMENT '알림 타입',
    title               VARCHAR(100) NOT NULL COMMENT '알림 제목',
    message             TEXT NOT NULL COMMENT '알림 본문',
    deep_link           VARCHAR(500) NULL COMMENT '앱 이동 경로',
    payload             JSON NULL COMMENT '프론트 보조 데이터',
    is_read             BOOLEAN NOT NULL DEFAULT FALSE COMMENT '읽음 여부',
    read_at             TIMESTAMP NULL COMMENT '읽음 처리 시각',
    sent_at             TIMESTAMP NULL COMMENT '푸시 발송 시각',
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 시각',
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 시각',

    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_notifications_product FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE SET NULL,
    CONSTRAINT fk_notifications_device FOREIGN KEY (device_id) REFERENCES devices (id) ON DELETE SET NULL,
    CONSTRAINT fk_notifications_reservation FOREIGN KEY (reservation_id) REFERENCES reservations (id) ON DELETE SET NULL,
    CONSTRAINT fk_notifications_care_report FOREIGN KEY (care_report_id) REFERENCES care_reports (id) ON DELETE SET NULL,
    CONSTRAINT fk_notifications_care_suggestion FOREIGN KEY (care_suggestion_id) REFERENCES care_suggestions (id) ON DELETE SET NULL,
    CONSTRAINT ck_notifications_type CHECK (notification_type IN (
        'CARE_TIMING',
        'RESERVATION_REMINDER',
        'DEVICE_STATUS',
        'ENVIRONMENT_ALERT'
    ))
);

CREATE INDEX idx_notifications_user_created ON notifications (user_id, created_at);
CREATE INDEX idx_notifications_user_read ON notifications (user_id, is_read);
CREATE INDEX idx_notifications_type ON notifications (notification_type);
CREATE INDEX idx_notifications_product ON notifications (product_id);
CREATE INDEX idx_notifications_device ON notifications (device_id);
CREATE INDEX idx_notifications_reservation ON notifications (reservation_id);
CREATE INDEX idx_notifications_care_report ON notifications (care_report_id);
CREATE INDEX idx_notifications_care_suggestion ON notifications (care_suggestion_id);
