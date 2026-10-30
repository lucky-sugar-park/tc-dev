-- 001 긴 글 칼럼을 LONGTEXT 로 넓힌다 (2026-10-30)
--
-- 엔티티에 @Lob 만 있으면 MariaDB 에서 TINYTEXT(255바이트)로, @Column 만 있으면 VARCHAR(255)로 만들어져
-- 그보다 긴 값은 "Data too long for column" 으로 저장 자체가 실패한다. 엔티티는 LONGTEXT 로 고쳤지만
-- ddl-auto=update 는 이미 있는 칼럼의 형을 바꾸지 않으므로, 기존 DB 에는 이 스크립트를 돌린다.
--
-- 넓히기만 하므로 값은 바뀌지 않는다. 앱을 멈추지 않아도 되지만, 표를 쥔 요청이 있으면 오래 기다리지 않게 한다.
-- 대상 DB: tc (vplc 가 같은 DB 를 쓰면 vplc_history 도 포함)

SET SESSION lock_wait_timeout = 10;

ALTER TABLE exchanged_message_audit MODIFY MSG_JSON LONGTEXT NULL;
ALTER TABLE exchanged_message_audit MODIFY RECEIVER VARCHAR(1024) NULL;
ALTER TABLE plc_connector           MODIFY REQ_HEADER_TEMPLATE LONGTEXT NULL;
ALTER TABLE plc_connector           MODIFY RES_HEADER_TEMPLATE LONGTEXT NULL;
ALTER TABLE plc_connection_hist     MODIFY EVENT_JSON LONGTEXT NULL;
ALTER TABLE user_hist               MODIFY REMARK LONGTEXT NULL;
ALTER TABLE webhook                 MODIFY SAMPLE_DATA LONGTEXT NULL;

-- vplc 테이블 — vplc 가 다른 DB 를 쓰면 그 DB 에서 돌린다
ALTER TABLE vplc_history            MODIFY HIST_CONTENTS LONGTEXT NULL;

-- 확인
SELECT table_name, column_name, column_type
  FROM information_schema.columns
 WHERE table_schema = DATABASE()
   AND (table_name, column_name) IN (
       ('exchanged_message_audit', 'msg_json'), ('exchanged_message_audit', 'receiver'),
       ('plc_connector', 'req_header_template'), ('plc_connector', 'res_header_template'),
       ('plc_connection_hist', 'event_json'), ('user_hist', 'remark'),
       ('webhook', 'sample_data'), ('vplc_history', 'hist_contents'));
