-- 010 인터페이스 settings (2026-10-31, docs/interface-detail-design.md §3·B단계)
--
-- 인터페이스마다 커넥터 설정을 덮어쓰는 값(MELSEC 국 번호·감시 타이머, Modbus unitId …)과 인터페이스 전용 값(mergeGap, writeFunction)을
-- JSON 으로 둔다. 비면 커넥터 설정 그대로 — 기존 인터페이스의 동작은 바뀌지 않는다.
-- ddl-auto=update 인 환경은 앱이 만든다. 다시 돌려도 안전하다.
-- 대상 DB: tc

SET SESSION lock_wait_timeout = 10;

ALTER TABLE interface ADD COLUMN IF NOT EXISTS settings_json LONGTEXT NULL;
