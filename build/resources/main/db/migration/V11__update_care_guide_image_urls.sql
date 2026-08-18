UPDATE care_guides
SET guide_image_url = 'http://161.33.38.65:8080/images/natural_leather.png',
    updated_at = CURRENT_TIMESTAMP(6)
WHERE material_id = 'natural_leather'
  AND material_subtype IS NULL;

UPDATE care_guides
SET guide_image_url = 'http://161.33.38.65:8080/images/canvas.png',
    updated_at = CURRENT_TIMESTAMP(6)
WHERE material_id = 'canvas'
  AND material_subtype IS NULL;
