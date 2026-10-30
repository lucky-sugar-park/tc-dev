-- 002 API 클라이언트·키와 감사 PRINCIPAL (2026-10-30, docs/auth-and-audit-design.md §6)
--
-- ddl-auto=update 인 환경은 앱이 기동할 때 같은 표와 칼럼을 만든다. ddl-auto 를 끈 운영 DB 에는 이 스크립트를 먼저 돌린다.
-- 새 표 두 개와 감사 표의 칼럼 하나를 더할 뿐이라 기존 값은 바뀌지 않는다. 앱을 멈추지 않아도 된다.
-- 대상 DB: tc

SET SESSION lock_wait_timeout = 10;

-- 타 시스템 하나. 삭제는 DELETED_AT 만 남긴다(삭제한 CLIENT_ID 재사용 금지).
CREATE TABLE IF NOT EXISTS api_client (
    CLIENT_ID                 VARCHAR(100)  NOT NULL,
    NAME                      VARCHAR(200)  NOT NULL,
    DESCRIPTION               VARCHAR(1024) NULL,
    ENABLED                   BIT(1)        NOT NULL,
    ALLOWED_OPCODES           VARCHAR(4000) NULL,     -- 쉼표 목록. * 전체, 끝이 * 이면 접두 패턴
    ALLOWED_IPS               VARCHAR(2000) NULL,     -- 쉼표 목록. IP 또는 CIDR. 비우면 제한 없음
    ALLOWED_PUSH_URL_PREFIXES VARCHAR(4000) NULL,     -- 쉼표 목록
    RATE_LIMIT_PER_SEC        INT           NULL,     -- 비우면 tc.api-client.default-rate-limit-per-sec
    MAX_CONCURRENT            INT           NULL,     -- 비우면 tc.api-client.default-max-concurrent
    DELETED_AT                DATETIME(6)   NULL,
    CREATED_DATE              DATETIME(6)   NULL,
    UPDATED_DATE              DATETIME(6)   NULL,
    CREATED_BY                VARCHAR(255)  NULL,
    UPDATED_BY                VARCHAR(255)  NULL,
    PRIMARY KEY (CLIENT_ID)
);

-- 키 하나가 행 하나. 비밀 부분은 SHA-256 해시로만 둔다.
CREATE TABLE IF NOT EXISTS api_client_key (
    KEY_ID       VARCHAR(40)  NOT NULL,
    CLIENT_ID    VARCHAR(100) NOT NULL,
    KEY_HASH     VARCHAR(64)  NOT NULL,
    STATUS       VARCHAR(20)  NOT NULL,              -- ACTIVE, REVOKED
    EXPIRES_AT   DATETIME(6)  NOT NULL,
    REVOKED_AT   DATETIME(6)  NULL,
    LAST_USED_AT DATETIME(6)  NULL,
    LAST_USED_IP VARCHAR(64)  NULL,
    CREATED_DATE DATETIME(6)  NULL,
    UPDATED_DATE DATETIME(6)  NULL,
    CREATED_BY   VARCHAR(255) NULL,
    UPDATED_BY   VARCHAR(255) NULL,
    PRIMARY KEY (KEY_ID),
    INDEX IX_API_CLIENT_KEY_CLIENT (CLIENT_ID)
);

-- 실제 호출자(client:{id}, user:{id}, system). 본문의 SENDER 는 호출자가 쓰는 값이라 따로 둔다.
ALTER TABLE exchanged_message_audit ADD COLUMN IF NOT EXISTS PRINCIPAL VARCHAR(200) NULL;
