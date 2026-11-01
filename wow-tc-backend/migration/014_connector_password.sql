-- 014 PLC 커넥터 로그인 비밀번호 (2026-11-01, docs/opcua-design.md O13)
--
-- OPC UA 커넥터의 사용자 이름 로그인(authMode=USERNAME) 비밀번호 — TC_SECRET_KEY 로 암호화한 값(v1:…)만 둔다. 조회 API 는 passwordSet 만 돌려준다.
-- ddl-auto=update 인 환경은 앱이 만든다. 다시 돌려도 안전하다. 대상 DB: tc

SET SESSION lock_wait_timeout = 10;

ALTER TABLE plc_connector ADD COLUMN IF NOT EXISTS password_enc VARCHAR(1024) NULL;
