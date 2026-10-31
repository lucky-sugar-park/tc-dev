-- 011 RAW 인터페이스 제거 (2026-10-31, docs/interfaces.md, 결정 I7)
--
-- 인터페이스는 이제 점 목록(INTERFACE_POINT)으로만 정의한다 — 정의 방식(MODE) 구분이 없다. 운영 중인 시스템이 없어 RAW 를 옮기지 않고 지운다.
-- 1) 점으로 정의되지 않은 인터페이스(MODE 가 RAW·NULL, 점이 없음, IF_TYPE 이 READ·WRITE 가 아님 — 옛 REPLY·PUSH)를
--    점·전달 정의와 함께 지운다.
-- 2) RAW 전용 표 HEADER_PROP·DATA_PROP 과 RAW 전용 칼럼(응답 인터페이스 이름, 데이터 길이, 커맨드 클래스, 정의 방식)을 지운다.
-- 3) 커넥터의 헤더 템플릿 칼럼(REQ/RES_HEADER_TEMPLATE)을 지운다 — RAW 헤더의 기본값이었고 읽는 곳이 없다.
-- 4) RAW·변환 화면 문구(I18N_ITEM)를 지운다 — 씨앗에서 뺀 키. 앱은 없는 줄만 넣으므로 DB 의 옛 줄은 남아 있다.
-- ddl-auto=update 는 칼럼·표를 지우지 않으므로 이 스크립트를 돌려야 한다(옛 칼럼이 NOT NULL 이 아니라 앱은 돌려도 그 전에 뜬다).
-- 다시 돌려도 안전하다. 대상 DB: tc

SET SESSION lock_wait_timeout = 10;

-- RAW 헤더·데이터 속성 (INTERFACE 를 참조하므로 먼저)
DROP TABLE IF EXISTS header_prop;
DROP TABLE IF EXISTS data_prop;

-- 지울 인터페이스 — MODE 칼럼이 남아 있으면(첫 실행) RAW·NULL 도 고른다
CREATE TEMPORARY TABLE IF NOT EXISTS tmp_raw_interface (id BIGINT PRIMARY KEY, name VARCHAR(255));
DELETE FROM tmp_raw_interface;

INSERT IGNORE INTO tmp_raw_interface (id, name)
SELECT i.id, i.name FROM interface i
 WHERE i.if_type IS NULL OR i.if_type NOT IN ('READ', 'WRITE')
    OR NOT EXISTS (SELECT 1 FROM interface_point p WHERE p.interface_id = i.id);

SET @has_mode = (SELECT COUNT(*) FROM information_schema.columns
                  WHERE table_schema = DATABASE() AND table_name = 'interface' AND column_name = 'mode');
SET @sql = IF(@has_mode > 0,
    'INSERT IGNORE INTO tmp_raw_interface (id, name) SELECT id, name FROM interface WHERE mode = ''RAW'' OR mode IS NULL',
    'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

DELETE FROM interface_point     WHERE interface_id   IN (SELECT id   FROM tmp_raw_interface);
DELETE FROM interface_delivery  WHERE interface_name IN (SELECT name FROM tmp_raw_interface);
DELETE FROM interface           WHERE id             IN (SELECT id   FROM tmp_raw_interface);

DROP TEMPORARY TABLE IF EXISTS tmp_raw_interface;

ALTER TABLE interface DROP COLUMN IF EXISTS reply_name;
ALTER TABLE interface DROP COLUMN IF EXISTS data_len;
ALTER TABLE interface DROP COLUMN IF EXISTS cmd_class_name;
ALTER TABLE interface DROP COLUMN IF EXISTS use_generic_cmd_class;
ALTER TABLE interface DROP COLUMN IF EXISTS mode;

ALTER TABLE plc_connector DROP COLUMN IF EXISTS req_header_template;
ALTER TABLE plc_connector DROP COLUMN IF EXISTS res_header_template;

-- 화면 문구 — RAW 보기·변환·되돌리기
DELETE FROM i18n_item
 WHERE code LIKE 'i18n.tc.raw.%'
    OR code LIKE 'i18n.tc.convert.%'
    OR code IN ('i18n.tc.interface.mode', 'i18n.tc.interface.replyIf', 'i18n.tc.interface.revertToRaw',
                'i18n.tc.interface.confirmRevert', 'i18n.tc.interface.revertedToRaw', 'i18n.tc.interface.noRawDefinition');
