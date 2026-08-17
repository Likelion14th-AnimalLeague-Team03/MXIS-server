ALTER TABLE users
    ADD COLUMN primary_product_id BIGINT NULL COMMENT '사용자의 대표 제품 ID' AFTER phone,
    ADD INDEX idx_users_primary_product_id (primary_product_id),
    ADD CONSTRAINT fk_users_primary_product
        FOREIGN KEY (primary_product_id) REFERENCES products (id)
        ON DELETE SET NULL;
