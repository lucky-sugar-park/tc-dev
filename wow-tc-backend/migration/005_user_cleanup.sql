-- 005 사용자 정리 (2026-10-30, docs/auth-and-audit-design.md §3.1)
--
-- 1) 새 칼럼: 비밀번호 변경 강제·변경 시각, 마지막 로그인 시각·주소. ddl-auto=update 인 환경은 앱이 만든다.
-- 2) enabled 가 NULL 인 예전 행을 사용 중(1)으로 — 예전 코드는 이 값을 검사하지 않았고, 이제는 검사한다.
--    앱도 NULL 을 사용 중으로 보지만, 값을 분명히 해 둔다.
-- 3) 더 쓰지 않는 칼럼(phone, extra_info, logined, logined_ip)은 앱이 읽지 않는다. 지우는 것은 되돌릴 수 없으므로
--    아래 DROP 문은 주석으로 둔다 — 백업을 확인한 뒤 따로 실행한다.
-- 대상 DB: tc

SET SESSION lock_wait_timeout = 10;

ALTER TABLE user ADD COLUMN IF NOT EXISTS password_must_change BIT(1)      NULL;
ALTER TABLE user ADD COLUMN IF NOT EXISTS password_changed_at  DATETIME(6) NULL;
ALTER TABLE user ADD COLUMN IF NOT EXISTS last_login_at        DATETIME(6) NULL;
ALTER TABLE user ADD COLUMN IF NOT EXISTS last_login_ip        VARCHAR(64) NULL;

UPDATE user SET enabled = 1 WHERE enabled IS NULL;

-- 백업 확인 뒤 실행:
-- ALTER TABLE user DROP COLUMN IF EXISTS phone;
-- ALTER TABLE user DROP COLUMN IF EXISTS extra_info;
-- ALTER TABLE user DROP COLUMN IF EXISTS logined;
-- ALTER TABLE user DROP COLUMN IF EXISTS logined_ip;
