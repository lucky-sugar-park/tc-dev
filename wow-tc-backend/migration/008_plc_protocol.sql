-- 008 PLC 프로토콜 일반화 1단계 (2026-10-31, docs/plc-protocol-design.md §4·§8)
--
-- 1) 커넥터: 프로토콜·프로토콜별 설정(JSON). 기존 커넥터는 MELSEC_MC — 설정이 비어 있으면 앱이 옛 FRAME_FORMAT 으로 기본값을 채운다.
-- 2) 인터페이스: 정의 방식(RAW·POINTS)과 쓰기 뒤 다시 읽기. 기존 인터페이스는 RAW — 동작이 바뀌지 않는다(결정 P1).
-- 3) 점 표 INTERFACE_POINT(결정 P2).
-- ddl-auto=update 인 환경은 앱이 만든다. 다시 돌려도 안전하다.
-- 대상 DB: tc

SET SESSION lock_wait_timeout = 10;

ALTER TABLE plc_connector ADD COLUMN IF NOT EXISTS protocol      VARCHAR(32) NULL;
ALTER TABLE plc_connector ADD COLUMN IF NOT EXISTS settings_json LONGTEXT    NULL;
UPDATE plc_connector SET protocol = 'MELSEC_MC' WHERE protocol IS NULL;

ALTER TABLE interface ADD COLUMN IF NOT EXISTS mode      VARCHAR(16) NULL;
ALTER TABLE interface ADD COLUMN IF NOT EXISTS read_back BIT(1)      NULL;
UPDATE interface SET mode = 'RAW' WHERE mode IS NULL;

CREATE TABLE IF NOT EXISTS interface_point (
    interface_id BIGINT        NOT NULL,
    name         VARCHAR(255)  NOT NULL,
    seq          INT           NULL,
    address      VARCHAR(200)  NOT NULL,
    type         VARCHAR(16)   NOT NULL,
    cnt          INT           NULL,
    len          INT           NULL,
    byte_order   VARCHAR(8)    NULL,
    word_order   VARCHAR(8)    NULL,
    description  VARCHAR(1024) NULL,
    PRIMARY KEY (interface_id, name),
    CONSTRAINT fk_interface_point_interface FOREIGN KEY (interface_id) REFERENCES interface (id)
);
