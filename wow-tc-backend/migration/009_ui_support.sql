-- 009 화면 지원 표 (2026-10-31, docs/frontend-design.md §4.4) — PLC 일반화 5단계(새 프론트)
--
-- mes-dev 와 같은 칼럼의 다국어·공통 코드·북마크 표. 프론트가 MES shared 패키지를 그대로 쓴다.
-- 문구·코드 씨앗은 앱이 기동 때 없는 줄만 넣는다(tc/src/main/resources/ui-seed, 설정 tc.ui.seed-enabled).
-- ddl-auto=update 인 환경은 앱이 만든다. 다시 돌려도 안전하다.
-- 대상 DB: tc

SET SESSION lock_wait_timeout = 10;

CREATE TABLE IF NOT EXISTS i18n_item (
    CODE         VARCHAR(255)  NOT NULL,
    LANG_CODE    VARCHAR(8)    NOT NULL,
    PARENT       VARCHAR(255)  NULL,
    KIND         VARCHAR(255)  NULL,
    VALUE        VARCHAR(2000) NULL,
    DESCRIPTION  VARCHAR(1024) NULL,
    IS_USE       BIT(1)        NULL,
    CREATED_DATE DATETIME(6)   NULL,
    UPDATED_DATE DATETIME(6)   NULL,
    CREATED_BY   VARCHAR(255)  NULL,
    UPDATED_BY   VARCHAR(255)  NULL,
    PRIMARY KEY (CODE, LANG_CODE),
    INDEX IX_I18N_ITEM_KIND (KIND),
    INDEX IX_I18N_ITEM_LANG (LANG_CODE)
);

CREATE TABLE IF NOT EXISTS common_code (
    CODE         VARCHAR(255)  NOT NULL,
    NAME         VARCHAR(255)  NULL,
    I18N_CODE    VARCHAR(255)  NULL,
    PARENT       VARCHAR(255)  NULL,
    DESCRIPTION  VARCHAR(1024) NULL,
    IS_USE       BIT(1)        NULL,
    CREATED_DATE DATETIME(6)   NULL,
    UPDATED_DATE DATETIME(6)   NULL,
    CREATED_BY   VARCHAR(255)  NULL,
    UPDATED_BY   VARCHAR(255)  NULL,
    PRIMARY KEY (CODE),
    INDEX IX_COMMON_CODE_PARENT (PARENT)
);

CREATE TABLE IF NOT EXISTS bookmark (
    USER_ID      VARCHAR(128)  NOT NULL,
    NAME         VARCHAR(200)  NOT NULL,
    PATH_NAME    VARCHAR(1000) NULL,
    DESCRIPTION  VARCHAR(1000) NULL,
    STATE_JSON   VARCHAR(4000) NULL,
    CREATED_DATE DATETIME(6)   NULL,
    UPDATED_DATE DATETIME(6)   NULL,
    CREATED_BY   VARCHAR(255)  NULL,
    UPDATED_BY   VARCHAR(255)  NULL,
    PRIMARY KEY (USER_ID, NAME)
);
