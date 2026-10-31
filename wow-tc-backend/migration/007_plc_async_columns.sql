-- 007 PLC 커넥터의 비동기 큐 설정 칼럼 정리 (2026-10-31, docs/backend-todo.md #15)
--
-- 어댑터의 비동기 요청 큐(타이머)는 부르는 곳이 없는 죽은 코드라 지웠다. 앱은 아래 세 칼럼을 더 읽거나 쓰지 않는다.
-- 1) 새 커넥터를 등록할 때 이 칼럼에 값을 넣지 않으므로, NOT NULL 로 만들어진 환경이면 NULL 을 허용하게 바꾼다.
--    ddl-auto=update 로 만들어진 칼럼은 이미 NULL 허용이라 바뀌는 것이 없다.
-- 2) 지우는 것은 되돌릴 수 없으므로 DROP 문은 주석으로 둔다 — 백업을 확인한 뒤 따로 실행한다.
-- 대상 DB: tc

SET SESSION lock_wait_timeout = 10;

ALTER TABLE plc_connector MODIFY COLUMN IF EXISTS async_req_use            BIT(1) NULL;
ALTER TABLE plc_connector MODIFY COLUMN IF EXISTS async_timer_start_delay  BIGINT NULL;
ALTER TABLE plc_connector MODIFY COLUMN IF EXISTS async_timer_run_interval BIGINT NULL;

-- 백업 확인 뒤 실행:
-- ALTER TABLE plc_connector DROP COLUMN IF EXISTS async_req_use;
-- ALTER TABLE plc_connector DROP COLUMN IF EXISTS async_timer_start_delay;
-- ALTER TABLE plc_connector DROP COLUMN IF EXISTS async_timer_run_interval;
