-- 013 점 배율·오프셋·단위·쓰기 범위 (2026-11-01, docs/interface-detail-design.md §9.2, 결정 I3)
--
-- 공학 값 = 원시 값 × SCALE + OFFSET_VALUE. MIN_VALUE·MAX_VALUE 는 쓰기 값 범위(공학 값). UNIT 은 표시용.
-- 모두 비울 수 있다(비면 배율 1·오프셋 0·범위 없음). 워드 안 비트(D100.3)는 주소 문법이라 칼럼이 없다.
-- ddl-auto=update 인 환경은 앱이 만든다. 다시 돌려도 안전하다. 대상 DB: tc

SET SESSION lock_wait_timeout = 10;

ALTER TABLE interface_point ADD COLUMN IF NOT EXISTS scale        DOUBLE      NULL;
ALTER TABLE interface_point ADD COLUMN IF NOT EXISTS offset_value DOUBLE      NULL;
ALTER TABLE interface_point ADD COLUMN IF NOT EXISTS unit         VARCHAR(32) NULL;
ALTER TABLE interface_point ADD COLUMN IF NOT EXISTS min_value    DOUBLE      NULL;
ALTER TABLE interface_point ADD COLUMN IF NOT EXISTS max_value    DOUBLE      NULL;
