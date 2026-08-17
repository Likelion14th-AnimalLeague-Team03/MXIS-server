-- 매장별 예약 가능 시간을 지원하기 위한 구조화 컬럼.
--
-- opening_hours는 사람이 읽는 안내 문구라 파싱할 수 없어, 예약 슬롯 계산에 쓸 수 있는
-- open_time/close_time을 따로 둔다. 두 값은 같은 사실을 표현하므로 한쪽만 바꾸면 안 된다.
-- 예약 슬롯은 open_time부터 30분 간격이며 마지막 슬롯은 close_time 30분 전이다
-- (close_time에 시작하는 예약은 만들지 않는다).

ALTER TABLE stores
    ADD COLUMN open_time  TIME NOT NULL DEFAULT '10:00:00' COMMENT '예약 가능 시작 시각' AFTER opening_hours,
    ADD COLUMN close_time TIME NOT NULL DEFAULT '19:00:00' COMMENT '예약 가능 종료 시각' AFTER open_time;

-- 기존 시드 매장의 opening_hours 안내 문구와 값을 일치시킨다.
UPDATE stores SET open_time = '11:00:00', close_time = '20:00:00' WHERE store_name = 'MCM 청담 플래그십';
UPDATE stores SET open_time = '10:30:00', close_time = '20:00:00' WHERE store_name = 'MCM 신세계 강남점';
