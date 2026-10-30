-- 006 DB_TABLE 결과 전달 (2026-10-31, docs/db-table-delivery.md)
--
-- ddl-auto=update 인 환경은 앱이 기동할 때 같은 표와 칼럼을 만든다. ddl-auto 를 끈 운영 DB 에는 이 스크립트를 먼저 돌린다.
-- 새 표 하나와 전달 정의 표의 칼럼 하나를 더할 뿐이라 기존 값은 바뀌지 않는다. 앱을 멈추지 않아도 된다.
-- 대상 DB: tc
--
-- 결과를 받을 테이블(표준 칼럼)은 tc DB 가 아니라 받는 시스템의 DB 에 만든다 — docs/db-table-delivery.md §2

SET SESSION lock_wait_timeout = 10;

-- 결과를 기록할 외부 DB 연결. 비밀번호는 TC_SECRET_KEY 로 암호화한 값(v1:…)만 둔다.
CREATE TABLE IF NOT EXISTS db_connection (
    NAME               VARCHAR(100)  NOT NULL,
    JDBC_URL           VARCHAR(1000) NOT NULL,
    USERNAME           VARCHAR(200)  NULL,
    ENCRYPTED_PASSWORD VARCHAR(1000) NULL,
    MAX_POOL_SIZE      INT           NOT NULL,
    IS_USE             BIT(1)        NOT NULL,
    DESCRIPTION        VARCHAR(1024) NULL,
    CREATED_DATE       DATETIME(6)   NULL,
    UPDATED_DATE       DATETIME(6)   NULL,
    CREATED_BY         VARCHAR(255)  NULL,
    UPDATED_BY         VARCHAR(255)  NULL,
    PRIMARY KEY (NAME)
);

-- DB_TABLE 전달의 테이블 이름(TARGET_NAME 은 DB 연결 이름)
ALTER TABLE interface_delivery ADD COLUMN IF NOT EXISTS TABLE_NAME VARCHAR(200) NULL;
