CREATE TABLE care_guides (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '관리 가이드 고유 번호',
    material_id        VARCHAR(50) NOT NULL COMMENT '소재 ID',
    material_subtype   VARCHAR(50) NULL COMMENT '세부 소재. NULL이면 소재 공통 가이드',
    guide_image_url    VARCHAR(500) NULL COMMENT '관리 가이드 대표 이미지 주소',
    title              VARCHAR(100) NOT NULL COMMENT '일상 관리 가이드 한줄',
    description        TEXT NOT NULL COMMENT '일상 관리 가이드 설명',
    steps              JSON NOT NULL COMMENT '관리 방법 순서',
    tip                TEXT NULL COMMENT '관리 팁',
    is_active          BOOLEAN NOT NULL DEFAULT TRUE COMMENT '활성 여부',
    created_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_care_guides_material ON care_guides (material_id, material_subtype, is_active);

INSERT INTO care_guides (
    material_id, material_subtype, guide_image_url, title, description, steps, tip, is_active
) VALUES
(
    'natural_leather',
    NULL,
    'https://static.mcmworldwide.com/demo/care-guide/natural-leather.jpg',
    '마른 부드러운 천으로 표면 정돈',
    '먼지와 오염을 부드럽게 제거하여 가죽의 컨디션을 유지해 주세요.',
    JSON_ARRAY('마른 부드러운 천을 준비해주세요.', '결 방향을 따라 부드럽게 닦아주세요.', '강한 힘을 주지 않고 가볍게 닦아주세요.'),
    '정기적으로 관리하면 가죽의 광택과 수명을 오래 유지할 수 있어요.',
    TRUE
),
(
    'canvas',
    NULL,
    'https://static.mcmworldwide.com/demo/care-guide/canvas.jpg',
    '부드러운 천으로 가볍게 닦기',
    '표면의 먼지를 가볍게 제거하고 습한 상태로 오래 두지 않도록 관리해 주세요.',
    JSON_ARRAY('마른 천을 준비해주세요.', '표면을 가볍게 쓸어내듯 닦아주세요.', '물기나 오염이 남지 않도록 통풍되는 곳에 보관해주세요.'),
    '습도와 열이 높은 곳을 피하면 소재 컨디션 유지에 도움이 됩니다.',
    TRUE
);
