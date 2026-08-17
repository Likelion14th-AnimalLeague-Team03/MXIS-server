ALTER TABLE products
    ADD COLUMN material_display_name VARCHAR(100) NULL COMMENT '화면 표시용 소재/가죽명. 예: Visetos Canvas'
        AFTER material_id;

UPDATE products
SET material_display_name = CASE
    WHEN material_id = 'canvas' THEN 'Visetos Canvas'
    WHEN material_id = 'coated_canvas' THEN 'Visetos Canvas'
    WHEN material_id = 'natural_leather' THEN 'Spanish Nappa Leather'
    WHEN material_id = 'coated_cowhide' THEN 'Coated Cowhide Leather'
    WHEN material_id = 'suede' THEN 'Suede Leather'
    ELSE material_id
END
WHERE material_display_name IS NULL;
