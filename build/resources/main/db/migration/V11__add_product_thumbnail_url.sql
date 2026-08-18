-- 화면별로 큰 이미지(product_image_url)와 작은 이미지(썸네일)를 따로 관리하기 위한 컬럼.
ALTER TABLE products
    ADD COLUMN product_thumbnail_url VARCHAR(500) NULL AFTER product_image_url;
