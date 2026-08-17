ALTER TABLE products
    CHANGE COLUMN image_url product_image_url VARCHAR(500) NULL COMMENT '제품 이미지 주소';

ALTER TABLE devices
    ADD COLUMN device_image_url VARCHAR(500) NULL COMMENT 'Smart Charm 기기 이미지 주소'
        AFTER firmware_version;
