ALTER TABLE stores
    ADD COLUMN store_url VARCHAR(500) NULL COMMENT '매장 상세 페이지 링크' AFTER opening_hours;
