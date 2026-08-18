-- 무상 "정기 케어"(참 소유자 대상)와 유상 "AS" 예약을 구분하기 위한 컬럼.
-- CLAUDE.md에 명시돼 있던 의도적 갭(정기 케어 vs 유상 AS 구분 필드 없음)을 메운다.
-- 기존 행은 전부 무상 정기케어 흐름으로 생성됐으므로 FREE로 백필한다.

ALTER TABLE reservations
    ADD COLUMN reservation_type VARCHAR(10) NOT NULL DEFAULT 'FREE' COMMENT '무상 정기케어(FREE) / 유상 AS(PAID)' AFTER service_type,
    ADD CONSTRAINT ck_reservations_type CHECK (reservation_type IN ('FREE', 'PAID'));
