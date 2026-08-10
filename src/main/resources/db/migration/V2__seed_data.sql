-- 초기 케어 진단 알고리즘 (규칙 기반 v1) 및 샘플 매장 시드 데이터

INSERT INTO care_algorithms (version, name, description, rule_config, is_active, released_at)
VALUES (
    'v1.0.0',
    'Rule-Based Care Engine v1',
    'MVP 단계 규칙 기반 진단 알고리즘. 소재별 온습도 기준 및 충격 임계값으로 condition_grade를 산정한다.',
    JSON_OBJECT(
        'humidity_high_threshold', 70,
        'humidity_low_threshold', 30,
        'temperature_high_threshold', 35,
        'shock_threshold', 5.0
    ),
    TRUE,
    CURRENT_TIMESTAMP(6)
);

INSERT INTO stores (store_name, address, phone, latitude, longitude, opening_hours, is_active)
VALUES
    ('MCM 청담 플래그십', '서울특별시 강남구 압구정로 46길 20', '02-1234-5678', 37.5259700, 127.0393100, '매일 11:00-20:00', TRUE),
    ('MCM 신세계 강남점', '서울특별시 서초구 신반포로 176', '02-2345-6789', 37.5049900, 127.0038800, '매일 10:30-20:00', TRUE);
