-- Keep the existing material defaults and add five action-based guide types.
-- All visible fields come from the same catalog row.

INSERT INTO care_guides (material_id, material_subtype, care_type, guide_image_url, title, description, steps, tip, is_active)
SELECT 'natural_leather',
       NULL,
       'ventilated_shade_storage',
       'http://161.33.38.65:8080/images/ventilated_shade_storage.png',
       '직사광선을 피해 통풍이 잘되는 곳에 보관하세요.',
       '직사광선과 밀폐된 공간을 피해 보관 환경을 정돈해주세요.',
       JSON_ARRAY('직사광선이 닿지 않는 위치로 옮겨주세요.', '통풍이 잘되는 곳에 여유를 두고 보관해주세요.', '습기와 열이 많은 공간은 피해주세요.'),
       '보관 위치의 온도와 습도를 주기적으로 확인해주세요.',
       TRUE
WHERE NOT EXISTS (SELECT 1 FROM care_guides WHERE care_type = 'ventilated_shade_storage');

UPDATE care_guides
SET guide_image_url = 'http://161.33.38.65:8080/images/ventilated_shade_storage.png',
    title = '직사광선을 피해 통풍이 잘되는 곳에 보관하세요.',
    description = '직사광선과 밀폐된 공간을 피해 보관 환경을 정돈해주세요.',
    steps = JSON_ARRAY('직사광선이 닿지 않는 위치로 옮겨주세요.', '통풍이 잘되는 곳에 여유를 두고 보관해주세요.', '습기와 열이 많은 공간은 피해주세요.'),
    tip = '보관 위치의 온도와 습도를 주기적으로 확인해주세요.',
    is_active = TRUE,
    updated_at = CURRENT_TIMESTAMP(6)
WHERE care_type = 'ventilated_shade_storage';

INSERT INTO care_guides (material_id, material_subtype, care_type, guide_image_url, title, description, steps, tip, is_active)
SELECT 'canvas',
       NULL,
       'dry_soft_cloth_wipe',
       'http://161.33.38.65:8080/images/dry_soft_cloth_wipe.png',
       '마른 부드러운 천으로 표면을 정돈해주세요.',
       '표면의 먼지를 가볍게 정돈해주세요.',
       JSON_ARRAY('깨끗하고 마른 부드러운 천을 준비해주세요.', '표면의 먼지를 가볍게 쓸어내듯 닦아주세요.', '강하게 문지르지 말고 가볍게 마무리해주세요.'),
       '물이나 세정제를 사용하기 전에는 제품의 소재별 관리 안내를 확인해주세요.',
       TRUE
WHERE NOT EXISTS (SELECT 1 FROM care_guides WHERE care_type = 'dry_soft_cloth_wipe');

UPDATE care_guides
SET guide_image_url = 'http://161.33.38.65:8080/images/dry_soft_cloth_wipe.png',
    title = '마른 부드러운 천으로 표면을 정돈해주세요.',
    description = '표면의 먼지를 가볍게 정돈해주세요.',
    steps = JSON_ARRAY('깨끗하고 마른 부드러운 천을 준비해주세요.', '표면의 먼지를 가볍게 쓸어내듯 닦아주세요.', '강하게 문지르지 말고 가볍게 마무리해주세요.'),
    tip = '물이나 세정제를 사용하기 전에는 제품의 소재별 관리 안내를 확인해주세요.',
    is_active = TRUE,
    updated_at = CURRENT_TIMESTAMP(6)
WHERE care_type = 'dry_soft_cloth_wipe';

INSERT INTO care_guides (material_id, material_subtype, care_type, guide_image_url, title, description, steps, tip, is_active)
SELECT 'all',
       NULL,
       'ventilated_humidity_dry',
       'http://161.33.38.65:8080/images/ventilated_humidity_dry.png',
       '통풍이 잘되는 곳에서 충분히 습기를 식혀주세요.',
       '최근 습도 기록에 맞춰 습기가 머물지 않도록 보관 환경을 살펴봐주세요.',
       JSON_ARRAY('직사광선이 닿지 않는 통풍되는 곳으로 옮겨주세요.', '가방 주변에 공기가 흐를 수 있도록 여유 공간을 두어주세요.', '드라이어나 난방기 대신 자연스럽게 습기를 식혀주세요.'),
       '습한 상태로 밀폐된 공간에 오래 두지 않도록 해주세요.',
       TRUE
WHERE NOT EXISTS (SELECT 1 FROM care_guides WHERE care_type = 'ventilated_humidity_dry');

UPDATE care_guides
SET guide_image_url = 'http://161.33.38.65:8080/images/ventilated_humidity_dry.png',
    title = '통풍이 잘되는 곳에서 충분히 습기를 식혀주세요.',
    description = '최근 습도 기록에 맞춰 습기가 머물지 않도록 보관 환경을 살펴봐주세요.',
    steps = JSON_ARRAY('직사광선이 닿지 않는 통풍되는 곳으로 옮겨주세요.', '가방 주변에 공기가 흐를 수 있도록 여유 공간을 두어주세요.', '드라이어나 난방기 대신 자연스럽게 습기를 식혀주세요.'),
    tip = '습한 상태로 밀폐된 공간에 오래 두지 않도록 해주세요.',
    is_active = TRUE,
    updated_at = CURRENT_TIMESTAMP(6)
WHERE care_type = 'ventilated_humidity_dry';

INSERT INTO care_guides (material_id, material_subtype, care_type, guide_image_url, title, description, steps, tip, is_active)
SELECT 'all',
       NULL,
       'avoid_dry_storage',
       'http://161.33.38.65:8080/images/avoid_dry_storage.png',
       '건조한 환경을 피해 안정적인 곳에 보관해주세요.',
       '지나치게 건조한 장소를 피해 보관 환경을 일정하게 유지해주세요.',
       JSON_ARRAY('난방기나 에어컨 바람이 직접 닿는 곳을 피해 옮겨주세요.', '온도와 습도 변화가 크지 않은 곳에 보관해주세요.', '표면 상태를 가볍게 살펴봐주세요.'),
       '가방에 물을 직접 뿌리거나 임의로 보습제를 바르지 말고 소재별 관리 안내를 확인해주세요.',
       TRUE
WHERE NOT EXISTS (SELECT 1 FROM care_guides WHERE care_type = 'avoid_dry_storage');

UPDATE care_guides
SET guide_image_url = 'http://161.33.38.65:8080/images/avoid_dry_storage.png',
    title = '건조한 환경을 피해 안정적인 곳에 보관해주세요.',
    description = '지나치게 건조한 장소를 피해 보관 환경을 일정하게 유지해주세요.',
    steps = JSON_ARRAY('난방기나 에어컨 바람이 직접 닿는 곳을 피해 옮겨주세요.', '온도와 습도 변화가 크지 않은 곳에 보관해주세요.', '표면 상태를 가볍게 살펴봐주세요.'),
    tip = '가방에 물을 직접 뿌리거나 임의로 보습제를 바르지 말고 소재별 관리 안내를 확인해주세요.',
    is_active = TRUE,
    updated_at = CURRENT_TIMESTAMP(6)
WHERE care_type = 'avoid_dry_storage';

INSERT INTO care_guides (material_id, material_subtype, care_type, guide_image_url, title, description, steps, tip, is_active)
SELECT 'all',
       NULL,
       'avoid_heat_cool_down',
       'http://161.33.38.65:8080/images/avoid_heat_cool_down.png',
       '더운 환경을 피해 서늘한 곳에서 가방의 열기를 식혀주세요.',
       '최근 온도 기록에 맞춰 뜨거운 환경에서 벗어나 자연스럽게 열기를 식혀주세요.',
       JSON_ARRAY('직사광선이나 열원에서 떨어진 곳으로 옮겨주세요.', '그늘지고 통풍되는 서늘한 곳에 놓아주세요.', '열기가 가라앉은 뒤 표면과 형태를 가볍게 살펴봐주세요.'),
       '냉장고나 냉동고 대신 온도 변화가 완만한 실내에서 열기를 식혀주세요.',
       TRUE
WHERE NOT EXISTS (SELECT 1 FROM care_guides WHERE care_type = 'avoid_heat_cool_down');

UPDATE care_guides
SET guide_image_url = 'http://161.33.38.65:8080/images/avoid_heat_cool_down.png',
    title = '더운 환경을 피해 서늘한 곳에서 가방의 열기를 식혀주세요.',
    description = '최근 온도 기록에 맞춰 뜨거운 환경에서 벗어나 자연스럽게 열기를 식혀주세요.',
    steps = JSON_ARRAY('직사광선이나 열원에서 떨어진 곳으로 옮겨주세요.', '그늘지고 통풍되는 서늘한 곳에 놓아주세요.', '열기가 가라앉은 뒤 표면과 형태를 가볍게 살펴봐주세요.'),
    tip = '냉장고나 냉동고 대신 온도 변화가 완만한 실내에서 열기를 식혀주세요.',
    is_active = TRUE,
    updated_at = CURRENT_TIMESTAMP(6)
WHERE care_type = 'avoid_heat_cool_down';

INSERT INTO care_guides (material_id, material_subtype, care_type, guide_image_url, title, description, steps, tip, is_active)
SELECT 'all',
       NULL,
       'long_term_storage_check',
       'http://161.33.38.65:8080/images/long_term_storage_check.png',
       '오랜 시간 보관했다면 가볍게 꺼내 상태를 살펴봐주세요.',
       '오래 보관한 가방은 꺼내어 표면과 형태, 보관 환경을 확인해주세요.',
       JSON_ARRAY('보관 중인 가방을 조심스럽게 꺼내주세요.', '표면과 형태, 금속 장식을 가볍게 살펴봐주세요.', '습기나 눌림이 없는지 확인하고 통풍되는 곳에 다시 보관해주세요.'),
       '평소와 다른 변화가 보이면 무리하게 손질하지 말고 전문가에게 확인해주세요.',
       TRUE
WHERE NOT EXISTS (SELECT 1 FROM care_guides WHERE care_type = 'long_term_storage_check');

UPDATE care_guides
SET guide_image_url = 'http://161.33.38.65:8080/images/long_term_storage_check.png',
    title = '오랜 시간 보관했다면 가볍게 꺼내 상태를 살펴봐주세요.',
    description = '오래 보관한 가방은 꺼내어 표면과 형태, 보관 환경을 확인해주세요.',
    steps = JSON_ARRAY('보관 중인 가방을 조심스럽게 꺼내주세요.', '표면과 형태, 금속 장식을 가볍게 살펴봐주세요.', '습기나 눌림이 없는지 확인하고 통풍되는 곳에 다시 보관해주세요.'),
    tip = '평소와 다른 변화가 보이면 무리하게 손질하지 말고 전문가에게 확인해주세요.',
    is_active = TRUE,
    updated_at = CURRENT_TIMESTAMP(6)
WHERE care_type = 'long_term_storage_check';

INSERT INTO care_guides (material_id, material_subtype, care_type, guide_image_url, title, description, steps, tip, is_active)
SELECT 'all',
       NULL,
       'shock_impact_check',
       'http://161.33.38.65:8080/images/shock_impact_check.png',
       '충격이 생각보다 많았어요. 가방의 상태와 금속 장식을 가볍게 확인해주세요.',
       '최근 충격 기록이 있어 표면과 형태, 금속 장식을 직접 살펴보는 것이 좋습니다.',
       JSON_ARRAY('가방을 안정적인 곳에 놓아주세요.', '표면과 형태, 손잡이 연결 부위를 가볍게 살펴봐주세요.', '지퍼와 금속 장식에 평소와 다른 변화가 있는지 확인해주세요.'),
       '충격 기록만으로 손상을 확정할 수 없어요. 이상이 보이면 전문가에게 확인해주세요.',
       TRUE
WHERE NOT EXISTS (SELECT 1 FROM care_guides WHERE care_type = 'shock_impact_check');

UPDATE care_guides
SET guide_image_url = 'http://161.33.38.65:8080/images/shock_impact_check.png',
    title = '충격이 생각보다 많았어요. 가방의 상태와 금속 장식을 가볍게 확인해주세요.',
    description = '최근 충격 기록이 있어 표면과 형태, 금속 장식을 직접 살펴보는 것이 좋습니다.',
    steps = JSON_ARRAY('가방을 안정적인 곳에 놓아주세요.', '표면과 형태, 손잡이 연결 부위를 가볍게 살펴봐주세요.', '지퍼와 금속 장식에 평소와 다른 변화가 있는지 확인해주세요.'),
    tip = '충격 기록만으로 손상을 확정할 수 없어요. 이상이 보이면 전문가에게 확인해주세요.',
    is_active = TRUE,
    updated_at = CURRENT_TIMESTAMP(6)
WHERE care_type = 'shock_impact_check';
