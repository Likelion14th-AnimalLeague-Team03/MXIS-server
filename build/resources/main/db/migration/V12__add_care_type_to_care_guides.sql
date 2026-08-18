ALTER TABLE care_guides
    ADD COLUMN care_type VARCHAR(50) NULL
        COMMENT '케어 방식 ID. 소재가 아니라 이번 주 추천 관리 액션을 나타낸다'
        AFTER material_subtype;

UPDATE care_guides
SET care_type = 'ventilated_shade_storage',
    guide_image_url = 'http://161.33.38.65:8080/images/natural_leather.png',
    title = '이번 주에는 직사광선을 피해 통풍이 잘되는 공간에 보관하세요',
    description = '최근 보관 환경을 기준으로 열과 습도 변화에 주의하는 것이 좋습니다.',
    steps = JSON_ARRAY(
        '직사광선이 닿지 않는 위치로 옮겨주세요.',
        '통풍이 잘되는 공간에 자연스럽게 보관해주세요.',
        '습한 공간이나 열이 많은 공간은 피해주세요.'
    ),
    tip = '보관 위치를 주기적으로 확인하면 특정 환경에 오래 노출되는 것을 줄일 수 있어요.',
    updated_at = CURRENT_TIMESTAMP(6)
WHERE material_id = 'natural_leather'
  AND material_subtype IS NULL;

UPDATE care_guides
SET care_type = 'dry_soft_cloth_wipe',
    guide_image_url = 'http://161.33.38.65:8080/images/canvas.png',
    title = '이번 주에는 마른 부드러운 천으로 표면을 정돈해주세요',
    description = '최근 사용 기록을 기준으로 가벼운 표면 정돈 중심의 관리가 적합합니다.',
    steps = JSON_ARRAY(
        '마른 부드러운 천을 준비해주세요.',
        '표면을 결 방향으로 가볍게 닦아주세요.',
        '강한 힘을 주지 않고 마무리해주세요.'
    ),
    tip = '짧은 주기로 가볍게 관리하면 소재의 컨디션을 일정하게 유지하는 데 도움이 됩니다.',
    updated_at = CURRENT_TIMESTAMP(6)
WHERE material_id = 'canvas'
  AND material_subtype IS NULL;

CREATE INDEX idx_care_guides_care_type ON care_guides (care_type, is_active);
