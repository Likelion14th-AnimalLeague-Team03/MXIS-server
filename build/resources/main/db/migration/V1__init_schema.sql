-- =====================================================
-- MXIS (Smart Charm) 초기 스키마 - MariaDB
--
-- 원본 ERD(DBML, PostgreSQL 기준)를 MariaDB로 이식하면서 아래 두 가지를 변경했다.
--
-- 1. updated_at 자동 갱신
--    원본: PostgreSQL DB Trigger
--    변경: Spring Data JPA Auditing (@EnableJpaAuditing) 이 애플리케이션 계층에서 관리.
--          DB 컬럼은 DEFAULT CURRENT_TIMESTAMP(6) 만 부여한다.
--
-- 2. Partial Unique Index (PostgreSQL의 WHERE 절 있는 UNIQUE INDEX)
--    MariaDB는 조건부(partial) UNIQUE INDEX 문법을 지원하지 않는다.
--    대신 "조건을 만족할 때만 값을 갖고, 아닐 때는 NULL"인 Virtual Generated Column을 만들고
--    그 컬럼에 UNIQUE INDEX를 거는 방식으로 동일한 제약을 구현한다.
--    (UNIQUE INDEX는 NULL 값끼리는 중복을 허용하므로, 조건을 만족하는 행에 대해서만
--     사실상의 유일성이 강제된다.)
--    적용 대상:
--      - product_devices.uq_active_primary_sensor
--      - care_algorithms.uq_active_care_algorithm
--      - reservations.uq_confirmed_reservation_slot
-- =====================================================

SET NAMES utf8mb4;

-- =====================================================
-- USERS
-- =====================================================

CREATE TABLE users (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '회원 고유 번호',
    email             VARCHAR(255) NOT NULL COMMENT '로그인 아이디로 사용하는 이메일',
    password_hash     VARCHAR(255) NULL COMMENT '암호화된 비밀번호. 소셜 로그인 사용자는 NULL 가능',
    provider          VARCHAR(20) NOT NULL DEFAULT 'local' COMMENT '가입 경로',
    provider_uid      VARCHAR(255) NULL COMMENT '소셜 로그인 제공자가 발급한 고유 ID',
    name              VARCHAR(50) NOT NULL COMMENT '회원 이름',
    phone             VARCHAR(20) NULL COMMENT '휴대전화 번호',
    created_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted_at        DATETIME(6) NULL COMMENT '회원 탈퇴 일시 / 소프트 삭제',

    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_provider_uid UNIQUE (provider, provider_uid),
    CONSTRAINT ck_users_provider CHECK (provider IN ('local', 'kakao'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_users_deleted_at ON users (deleted_at);


-- =====================================================
-- NOTIFICATION_SETTINGS
-- =====================================================

CREATE TABLE notification_settings (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '알림 설정 고유 번호',
    user_id                  BIGINT NOT NULL COMMENT '회원당 알림 설정 1행',
    care_timing_enabled      BOOLEAN NOT NULL DEFAULT TRUE COMMENT '케어 시점 제안 알림',
    reservation_enabled      BOOLEAN NOT NULL DEFAULT TRUE COMMENT '예약 확정 및 리마인드 알림',
    device_status_enabled    BOOLEAN NOT NULL DEFAULT TRUE COMMENT '기기 연결 및 배터리 관련 알림',
    marketing_enabled        BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'MCM 브랜드 소식 및 혜택 알림',
    push_permission_granted  BOOLEAN NOT NULL DEFAULT FALSE COMMENT '모바일 앱이 마지막으로 확인한 OS Push 권한 상태',
    push_token               VARCHAR(255) NULL COMMENT 'FCM/APNs 디바이스 푸시 토큰',
    created_at               DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at               DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT uq_notification_settings_user UNIQUE (user_id),
    CONSTRAINT fk_notification_settings_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;


-- =====================================================
-- CONSENTS (Immutable Event Log)
-- =====================================================

CREATE TABLE consents (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '동의 이벤트 고유 번호',
    user_id        BIGINT NOT NULL COMMENT '동의 또는 철회를 수행한 회원',
    consent_type   VARCHAR(30) NOT NULL COMMENT '약관 및 동의 항목',
    terms_version  VARCHAR(30) NOT NULL COMMENT '동의/철회 대상 약관 버전',
    action         VARCHAR(10) NOT NULL COMMENT 'AGREED 또는 REVOKED',
    occurred_at    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '실제 동의 또는 철회가 발생한 시각',
    created_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_consents_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_consents_type CHECK (consent_type IN ('TERMS_OF_SERVICE', 'PRIVACY', 'SENSOR_DATA', 'MARKETING')),
    CONSTRAINT ck_consents_action CHECK (action IN ('AGREED', 'REVOKED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_consents_user_type_occurred ON consents (user_id, consent_type, occurred_at);


-- =====================================================
-- DEVICES
-- =====================================================

CREATE TABLE devices (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Smart Charm 고유 번호',
    user_id             BIGINT NOT NULL COMMENT '기기 소유 회원',
    serial_number       VARCHAR(100) NOT NULL COMMENT 'Smart Charm 제품 일련번호',
    device_name         VARCHAR(50) NULL COMMENT '사용자가 확인하는 기기 이름',
    mac_address         VARCHAR(50) NULL COMMENT 'BLE 통신용 하드웨어 주소',
    firmware_version    VARCHAR(20) NULL COMMENT 'Smart Charm 펌웨어 버전',
    battery_level       INT NULL COMMENT '마지막으로 확인된 배터리 잔량 0~100 (%)',
    connection_status   VARCHAR(20) NOT NULL DEFAULT 'DISCONNECTED' COMMENT '서버가 마지막으로 확인한 연결 상태',
    last_synced_at      DATETIME(6) NULL COMMENT '해당 기기의 마지막 Sensor Sync 완료 시각',
    registered_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '회원 계정에 기기를 등록한 시각',
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted_at          DATETIME(6) NULL COMMENT '기기 삭제 일시 / 소프트 삭제',

    CONSTRAINT uq_devices_serial_number UNIQUE (serial_number),
    CONSTRAINT fk_devices_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_devices_battery_level CHECK (battery_level IS NULL OR (battery_level BETWEEN 0 AND 100)),
    CONSTRAINT ck_devices_connection_status CHECK (connection_status IN ('CONNECTED', 'DISCONNECTED', 'SYNCING', 'ERROR'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_devices_user_id ON devices (user_id);
CREATE INDEX idx_devices_connection_status ON devices (connection_status);
CREATE INDEX idx_devices_deleted_at ON devices (deleted_at);


-- =====================================================
-- PRODUCTS
-- =====================================================

CREATE TABLE products (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '사용자가 등록한 실제 제품 고유 번호',
    user_id        BIGINT NOT NULL COMMENT '제품 소유 회원',
    dpp_code       VARCHAR(100) NULL COMMENT 'MCM Digital Product Passport 식별 코드',
    product_name   VARCHAR(100) NOT NULL COMMENT '제품명',
    model_code     VARCHAR(50) NULL COMMENT '제품 모델 코드',
    material       VARCHAR(50) NOT NULL COMMENT '제품 소재. 진단 알고리즘의 핵심 입력',
    color          VARCHAR(30) NULL COMMENT '제품 색상',
    image_url      VARCHAR(500) NULL COMMENT '제품 이미지 주소',
    purchased_at   DATE NULL COMMENT '제품 구매일',
    registered_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '앱에 제품을 등록한 시각',
    created_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    deleted_at     DATETIME(6) NULL COMMENT '제품 삭제 일시 / 소프트 삭제',

    CONSTRAINT uq_products_dpp_code UNIQUE (dpp_code),
    CONSTRAINT fk_products_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_products_user_id ON products (user_id);
CREATE INDEX idx_products_material ON products (material);
CREATE INDEX idx_products_deleted_at ON products (deleted_at);


-- =====================================================
-- PRODUCT_DEVICES (N:M 연결 이력)
-- =====================================================

CREATE TABLE product_devices (
    id                          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '제품-기기 연결 고유 번호',
    product_id                  BIGINT NOT NULL COMMENT '연결된 제품',
    device_id                   BIGINT NOT NULL COMMENT '연결된 Smart Charm',
    role                        VARCHAR(20) NOT NULL DEFAULT 'SECONDARY' COMMENT '제품에서 해당 기기가 수행하는 역할',
    attached_at                 DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '제품에 기기를 연결한 시각',
    detached_at                 DATETIME(6) NULL COMMENT '연결 해제 시각. NULL이면 현재 연결 상태',
    created_at                  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at                  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    -- Partial Unique Index 대체용 Virtual Generated Column:
    -- role = 'PRIMARY_SENSOR' 이고 아직 연결 해제되지 않은(detached_at IS NULL) 경우에만
    -- product_id 값을 갖고, 그 외에는 NULL이 되어 UNIQUE 제약에서 제외된다.
    active_primary_product_id   BIGINT AS (
        CASE WHEN role = 'PRIMARY_SENSOR' AND detached_at IS NULL THEN product_id ELSE NULL END
    ) VIRTUAL,

    CONSTRAINT fk_product_devices_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_product_devices_device FOREIGN KEY (device_id) REFERENCES devices (id),
    CONSTRAINT ck_product_devices_role CHECK (role IN ('PRIMARY_SENSOR', 'SECONDARY')),
    CONSTRAINT uq_active_primary_sensor UNIQUE (active_primary_product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_product_devices_product_id ON product_devices (product_id);
CREATE INDEX idx_product_devices_device_id ON product_devices (device_id);
CREATE INDEX idx_product_devices_product_device_attached ON product_devices (product_id, device_id, attached_at);


-- =====================================================
-- SENSOR_READINGS (Immutable)
-- =====================================================

CREATE TABLE sensor_readings (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '센서 측정 데이터 고유 번호',
    product_id          BIGINT NOT NULL COMMENT '센서 데이터가 귀속되는 제품',
    device_id           BIGINT NOT NULL COMMENT '실제로 데이터를 측정한 Smart Charm',
    product_device_id   BIGINT NOT NULL COMMENT '측정 당시의 제품-기기 연결 관계',
    sequence_number     BIGINT NOT NULL COMMENT 'Smart Charm 내부 측정 순번. BLE/API 재전송 시 중복 방지',
    temperature         DECIMAL(5, 2) NULL COMMENT '측정 온도 (섭씨)',
    humidity             DECIMAL(5, 2) NULL COMMENT '측정 상대습도 (%)',
    max_shock_level     DECIMAL(6, 3) NULL COMMENT '해당 측정 구간에서 감지된 최대 충격 강도',
    is_outing           BOOLEAN NOT NULL DEFAULT FALSE COMMENT '해당 측정 시점에서 제품이 외출/사용 상태인지 여부',
    measured_at         DATETIME(6) NOT NULL COMMENT 'Smart Charm에서 실제 측정한 시각',
    synced_at           DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '앱을 통해 서버에 동기화된 시각',

    CONSTRAINT fk_sensor_readings_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_sensor_readings_device FOREIGN KEY (device_id) REFERENCES devices (id),
    CONSTRAINT fk_sensor_readings_product_device FOREIGN KEY (product_device_id) REFERENCES product_devices (id),
    CONSTRAINT uq_sensor_readings_device_sequence UNIQUE (device_id, sequence_number)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_sensor_readings_product_measured ON sensor_readings (product_id, measured_at);
CREATE INDEX idx_sensor_readings_device_measured ON sensor_readings (device_id, measured_at);
CREATE INDEX idx_sensor_readings_product_device_id ON sensor_readings (product_device_id);
CREATE INDEX idx_sensor_readings_measured_at ON sensor_readings (measured_at);


-- =====================================================
-- CARE_ALGORITHMS
-- =====================================================

CREATE TABLE care_algorithms (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '진단 알고리즘 버전 고유 번호',
    version         VARCHAR(20) NOT NULL COMMENT '예: v1.0.0',
    name            VARCHAR(100) NOT NULL COMMENT '알고리즘 이름',
    description     TEXT NULL COMMENT '알고리즘 및 판정 규칙 설명',
    rule_config     JSON NULL COMMENT '소재별 온습도 기준, 충격 임계값 등 진단 Rule 설정',
    is_active       BOOLEAN NOT NULL DEFAULT FALSE COMMENT '신규 CareReport 생성에 사용할 현재 버전 여부',
    released_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '버전 적용 시작일',
    deprecated_at   DATETIME(6) NULL COMMENT '버전 사용 종료일',
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    -- is_active=true인 행이 전체에서 최대 1개만 존재하도록 강제하는 Virtual Generated Column
    active_flag     TINYINT AS (CASE WHEN is_active = TRUE THEN 1 ELSE NULL END) VIRTUAL,

    CONSTRAINT uq_care_algorithms_version UNIQUE (version),
    CONSTRAINT uq_active_care_algorithm UNIQUE (active_flag)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_care_algorithms_is_active ON care_algorithms (is_active);


-- =====================================================
-- CARE_REPORTS (Immutable Snapshot)
-- =====================================================

CREATE TABLE care_reports (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '진단 리포트 고유 번호',
    product_id            BIGINT NOT NULL COMMENT '진단 대상 제품',
    algorithm_id          BIGINT NOT NULL COMMENT '리포트 생성 당시 사용한 알고리즘',
    condition_grade       VARCHAR(20) NOT NULL COMMENT '종합 케어 상태',
    summary_text          TEXT NOT NULL COMMENT '사용자에게 노출되는 상태 요약 문구',
    analysis_text         TEXT NULL COMMENT '사용 환경 및 패턴에 대한 상세 해석',
    recommendation_text   TEXT NULL COMMENT '권장 일상 관리 행동',
    period_start          DATETIME(6) NOT NULL COMMENT '분석 대상 기간 시작',
    period_end            DATETIME(6) NOT NULL COMMENT '분석 대상 기간 종료',
    avg_temperature       DECIMAL(5, 2) NULL COMMENT '분석 기간 평균 온도 Snapshot',
    max_temperature       DECIMAL(5, 2) NULL COMMENT '분석 기간 최고 온도 Snapshot',
    min_temperature       DECIMAL(5, 2) NULL COMMENT '분석 기간 최저 온도 Snapshot',
    avg_humidity          DECIMAL(5, 2) NULL COMMENT '분석 기간 평균 습도 Snapshot',
    outing_count          INT NULL COMMENT '분석 기간 내 추론된 외출 Session 횟수',
    shock_count           INT NULL COMMENT '진단 기준을 초과한 충격 이벤트 횟수',
    created_at            DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '진단 생성 일시',

    CONSTRAINT fk_care_reports_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_care_reports_algorithm FOREIGN KEY (algorithm_id) REFERENCES care_algorithms (id),
    CONSTRAINT ck_care_reports_grade CHECK (condition_grade IN ('STABLE', 'BALANCED', 'LIGHT_CARE', 'EXPERT_CHECK'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_care_reports_product_created ON care_reports (product_id, created_at);
CREATE INDEX idx_care_reports_condition_grade ON care_reports (condition_grade);
CREATE INDEX idx_care_reports_algorithm_id ON care_reports (algorithm_id);


-- =====================================================
-- CARE_SUGGESTIONS
-- =====================================================

CREATE TABLE care_suggestions (
    id                     BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '케어 제안 고유 번호',
    care_report_id         BIGINT NOT NULL COMMENT '제안의 근거가 된 CareReport',
    product_id             BIGINT NOT NULL COMMENT '케어 제안 대상 제품',
    message                TEXT NOT NULL COMMENT '사용자에게 노출할 케어 제안 메시지',
    reason_text            TEXT NULL COMMENT '케어를 제안한 데이터 기반 근거',
    recommended_service    VARCHAR(100) NULL COMMENT '추천 케어 또는 전문가 점검 종류',
    recommended_visit_from DATE NULL COMMENT '추천 방문 기간 시작',
    recommended_visit_to   DATE NULL COMMENT '추천 방문 기간 종료',
    status                 VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '제안 상태',
    is_read                BOOLEAN NOT NULL DEFAULT FALSE COMMENT '사용자가 제안을 확인했는지 여부',
    expires_at             DATETIME(6) NULL COMMENT '제안 만료 일시',
    created_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT uq_care_suggestions_report UNIQUE (care_report_id),
    CONSTRAINT fk_care_suggestions_report FOREIGN KEY (care_report_id) REFERENCES care_reports (id),
    CONSTRAINT fk_care_suggestions_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_care_suggestions_status CHECK (status IN ('ACTIVE', 'RESERVED', 'EXPIRED', 'CANCELLED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_care_suggestions_product_status ON care_suggestions (product_id, status);
CREATE INDEX idx_care_suggestions_status ON care_suggestions (status);


-- =====================================================
-- STORES
-- =====================================================

CREATE TABLE stores (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'MCM 매장 고유 번호',
    store_name      VARCHAR(100) NOT NULL COMMENT '매장명',
    address         VARCHAR(255) NOT NULL COMMENT '매장 주소',
    phone           VARCHAR(20) NULL COMMENT '매장 연락처',
    latitude        DECIMAL(10, 7) NULL COMMENT '위도',
    longitude       DECIMAL(10, 7) NULL COMMENT '경도',
    opening_hours   VARCHAR(255) NULL COMMENT '운영시간 안내',
    is_active       BOOLEAN NOT NULL DEFAULT TRUE COMMENT '현재 컨시어지 예약을 받을 수 있는 매장인지 여부',
    created_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_stores_lat_lng ON stores (latitude, longitude);
CREATE INDEX idx_stores_is_active ON stores (is_active);


-- =====================================================
-- RESERVATIONS
-- =====================================================

CREATE TABLE reservations (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '예약 고유 번호',
    user_id               BIGINT NOT NULL COMMENT '예약 회원',
    product_id            BIGINT NOT NULL COMMENT '케어 대상 제품',
    store_id              BIGINT NOT NULL COMMENT '방문 예정 매장',
    care_suggestion_id    BIGINT NULL COMMENT '예약의 계기가 된 케어 제안. 직접 예약 시 NULL',
    service_type          VARCHAR(100) NULL COMMENT '신청한 케어 또는 점검 유형',
    reserved_date         DATE NOT NULL COMMENT '예약 날짜',
    reserved_time         TIME NOT NULL COMMENT '예약 시간',
    customer_note         TEXT NULL COMMENT '고객 요청사항',
    status                VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED' COMMENT '예약 상태',
    cancelled_at          DATETIME(6) NULL COMMENT '예약 취소 시각',
    completed_at          DATETIME(6) NULL COMMENT '매장 방문 완료 시각',
    created_at            DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    -- status = 'CONFIRMED' 인 예약에 대해서만 (store_id, reserved_date, reserved_time) 유일성을 강제.
    -- CANCELLED 된 예약은 슬롯을 다시 점유할 수 있어야 하므로 이 컬럼은 NULL이 되어 제약에서 제외된다.
    active_slot_key       VARCHAR(80) AS (
        CASE WHEN status = 'CONFIRMED'
             THEN CONCAT(store_id, '_', reserved_date, '_', reserved_time)
             ELSE NULL END
    ) VIRTUAL,

    CONSTRAINT fk_reservations_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_reservations_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_reservations_store FOREIGN KEY (store_id) REFERENCES stores (id),
    CONSTRAINT fk_reservations_care_suggestion FOREIGN KEY (care_suggestion_id) REFERENCES care_suggestions (id),
    CONSTRAINT ck_reservations_status CHECK (status IN ('CONFIRMED', 'CANCELLED', 'COMPLETED')),
    CONSTRAINT uq_confirmed_reservation_slot UNIQUE (active_slot_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_reservations_user_status ON reservations (user_id, status);
CREATE INDEX idx_reservations_store_date ON reservations (store_id, reserved_date);
CREATE INDEX idx_reservations_care_suggestion_id ON reservations (care_suggestion_id);
CREATE INDEX idx_reservations_status ON reservations (status);
