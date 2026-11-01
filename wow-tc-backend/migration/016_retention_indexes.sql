-- 016 보관 정리·기간 검색용 시각 색인 (2026-11-01, docs/data-retention.md)
--
-- 송수신 감사(EXCHANGED_MESSAGE_AUDIT.TIMESTAMP)·PLC 연결 이력(PLC_CONNECTION_HIST.EVENT_TIMESTAMP)에 시각 색인이 없어
-- 기간 검색과 정리 Job 의 기준 키 찾기가 표 전체를 훑었다. 실행 이력(SCHEDULE_JOB_EXEC_HIST)은 015 에서 만들었다.
-- ddl-auto=update 인 환경은 앱이 기동하며 만들지만, 큰 표는 기동이 늦어지므로 배포 전에 이 스크립트로 먼저 만든다.
-- 온라인으로 만든다(ALGORITHM=INPLACE, LOCK=NONE — 만드는 동안에도 기록이 막히지 않는다). 다시 돌려도 안전하다. 대상 DB: tc

SET SESSION lock_wait_timeout = 10;

CREATE INDEX IF NOT EXISTS ix_ema_timestamp       ON exchanged_message_audit (timestamp)       ALGORITHM=INPLACE LOCK=NONE;
CREATE INDEX IF NOT EXISTS ix_pch_event_timestamp ON plc_connection_hist     (event_timestamp) ALGORITHM=INPLACE LOCK=NONE;
