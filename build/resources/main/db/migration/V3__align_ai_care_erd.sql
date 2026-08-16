-- AI Care Model ERD alignment.
-- Existing legacy columns are preserved to avoid destructive migration of historical data.

ALTER TABLE notification_settings
    ADD COLUMN environment_alert_enabled BOOLEAN NOT NULL DEFAULT TRUE
        COMMENT '온습도 등 보관 환경이 안정 범위를 벗어났을 때 알림'
        AFTER marketing_enabled;

ALTER TABLE products
    ADD COLUMN material_id VARCHAR(50) NULL
        COMMENT 'AI Rule Evaluator 소재 ID. coated_cowhide, natural_leather, suede, canvas 등'
        AFTER material,
    ADD COLUMN material_subtypes JSON NULL
        COMMENT '세부 소재 태그. grained_coated_cowhide, vachetta, coated_canvas 등'
        AFTER material_id;

UPDATE products
SET material_id = material
WHERE material_id IS NULL;

ALTER TABLE products
    MODIFY COLUMN material_id VARCHAR(50) NOT NULL
        COMMENT 'AI Rule Evaluator 소재 ID. coated_cowhide, natural_leather, suede, canvas 등',
    MODIFY COLUMN material VARCHAR(50) NULL
        COMMENT 'Legacy 소재 표시값. AI 판단에는 material_id를 사용',
    ADD INDEX idx_products_material_id (material_id);

ALTER TABLE sensor_readings
    MODIFY COLUMN max_shock_level DECIMAL(6, 3) NULL
        COMMENT '측정 구간 내 최대 동적 가속도(g). 손상 판정값이 아니라 handling exposure proxy',
    ADD COLUMN motion_count INT NULL
        COMMENT '해당 측정 구간 내 움직임 이벤트 수. handling/usage exposure proxy'
        AFTER max_shock_level;

ALTER TABLE care_reports
    ADD COLUMN analysis_window_days INT NOT NULL DEFAULT 7
        COMMENT '분석 기간 일수'
        AFTER period_end,
    ADD COLUMN data_status VARCHAR(30) NOT NULL DEFAULT 'SUFFICIENT'
        COMMENT 'NO_DATA, INSUFFICIENT_DATA, STALE_DATA, SUFFICIENT'
        AFTER analysis_window_days,
    ADD COLUMN condition_label VARCHAR(30) NULL
        COMMENT 'Excellent, Standard, Needs Attention, Collecting Data'
        AFTER data_status,
    ADD COLUMN condition_score INT NULL
        COMMENT '프론트 표시용 상태 점수. 데이터 부족 시 NULL'
        AFTER condition_label,
    ADD COLUMN primary_factor VARCHAR(50) NULL
        COMMENT '주요 관리 요인. humidity, temperature_heat, dryness, handling, usage_rest 등'
        AFTER condition_score,
    ADD COLUMN care_need VARCHAR(30) NULL
        COMMENT 'Rule Evaluator의 케어 필요도'
        AFTER primary_factor,
    ADD COLUMN inspection_need VARCHAR(30) NULL
        COMMENT 'NONE, CONDITIONAL, REQUIRED'
        AFTER care_need,
    ADD COLUMN ai_output JSON NULL
        COMMENT 'AI Output Contract 전체 응답 스냅샷. 프론트 전달용 aiCareSummary 결과를 생성 시점 기준으로 보존'
        AFTER summary_text,
    ADD COLUMN feature_summary JSON NULL
        COMMENT '평균 온습도, 누적 노출 시간, motionTotal 등 Feature Extractor 요약값'
        AFTER ai_output,
    ADD COLUMN stress_labels JSON NULL
        COMMENT 'humidity, temperatureHeat, dryness, handling, usageRest, uvLight 판단 결과'
        AFTER feature_summary,
    ADD COLUMN evidence JSON NULL
        COMMENT 'triggeredRules, matchedKbEntries 등 Rule Evaluator 판단 근거'
        AFTER stress_labels,
    ADD COLUMN copy_generation JSON NULL
        COMMENT 'OpenAI 설명문 생성 여부, model, error, rawResponseId 등 메타데이터'
        AFTER evidence,
    ADD INDEX idx_care_reports_product_period_end (product_id, period_end),
    ADD INDEX idx_care_reports_data_status (data_status),
    ADD INDEX idx_care_reports_condition_label (condition_label),
    ADD INDEX idx_care_reports_care_need (care_need),
    ADD INDEX idx_care_reports_inspection_need (inspection_need);

UPDATE care_reports
SET ai_output = JSON_OBJECT(
        'schemaVersion', 'legacy-care-report',
        'aiCareSummary', JSON_OBJECT(
            'generatedAt', DATE_FORMAT(created_at, '%Y-%m-%dT%H:%i:%sZ'),
            'dataSufficiency', JSON_OBJECT('status', data_status),
            'productCondition', JSON_OBJECT('label', condition_label, 'score', condition_score, 'summary', summary_text)
        )
    )
WHERE ai_output IS NULL;

ALTER TABLE care_reports
    MODIFY COLUMN ai_output JSON NOT NULL
        COMMENT 'AI Output Contract 전체 응답 스냅샷. 프론트 전달용 aiCareSummary 결과를 생성 시점 기준으로 보존';

ALTER TABLE reservations
    DROP CONSTRAINT ck_reservations_status,
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING_APPROVAL' COMMENT '예약 상태',
    ADD CONSTRAINT ck_reservations_status
        CHECK (status IN ('PENDING_APPROVAL', 'CONFIRMED', 'CANCELLED', 'COMPLETED'));
