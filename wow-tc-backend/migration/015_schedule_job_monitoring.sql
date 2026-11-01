-- 015 스케줄 Job 속성·실행 이력 정리 (2026-11-01, docs/schedule-jobs.md)
--
-- 1) 실행 이력에 실행한 인스턴스(INSTANCE_ID)·걸린 시간(ELAPSED_MS)을 더하고, 목록의 최근 실행·이력 조회·보관 정리용 색인을 만든다.
-- 2) 정리 Job(DataTableFlashJob)의 속성을 새 정의로 옮긴다 — serviceBeanName·methodName·targetName·flashByType 대신
--    cleanupTarget(정리할 표). savingDays 는 그대로.
-- 3) 빈 Job(PlcConnectorSpyScheduleJob — 클래스를 지웠다)을 지운다.
-- 4) 실행할 때마다 덮어쓰던 SCHED_NAME 의 'NO_ASSIGNED' 를 기본 스케줄러 이름(scheduler.name.default)으로 되돌린다.
-- 5) 속성 값 형을 소문자(string·int·long)로 맞춘다.
-- ddl-auto=update 인 환경은 앱이 칼럼·색인을 만든다(1). 2~5 는 이 스크립트로만 된다. 다시 돌려도 안전하다. 대상 DB: tc
-- 돌린 뒤 Quartz 의 JobDataMap 은 tc 재기동(모든 Job 다시 예약) 또는 Job 마다 "다시 예약" 으로 새 속성을 받는다.

SET SESSION lock_wait_timeout = 10;

-- 1) 실행 이력
ALTER TABLE schedule_job_exec_hist ADD COLUMN IF NOT EXISTS instance_id VARCHAR(200) NULL;
ALTER TABLE schedule_job_exec_hist ADD COLUMN IF NOT EXISTS elapsed_ms  BIGINT       NULL;
CREATE INDEX IF NOT EXISTS ix_sjeh_job_seq   ON schedule_job_exec_hist (job_name, seq_no);
CREATE INDEX IF NOT EXISTS ix_sjeh_exec_time ON schedule_job_exec_hist (exec_time);

-- 2) 정리 Job 속성
INSERT IGNORE INTO schedule_job_detail (schedule_job_id, entry_key, entry_value, entry_value_type, description)
SELECT d.schedule_job_id, 'cleanupTarget',
       CASE d.entry_value WHEN 'plcConnectionEventHistoryServiceBean' THEN 'PLC_CONNECTION_HIST' ELSE 'EXCHANGED_MESSAGE_AUDIT' END,
       'string', '정리할 표'
  FROM schedule_job_detail d
  JOIN schedule_job j ON j.id = d.schedule_job_id
 WHERE j.job_class_name LIKE '%DataTableFlashJob' AND d.entry_key = 'serviceBeanName';

DELETE d FROM schedule_job_detail d
  JOIN schedule_job j ON j.id = d.schedule_job_id
 WHERE j.job_class_name LIKE '%DataTableFlashJob'
   AND d.entry_key IN ('flashByType', 'serviceBeanName', 'methodName', 'targetName');

-- 3) 빈 Job — Quartz 쪽(qrtz_*)은 tc 가 기동하며 모든 Job 을 다시 예약할 때 정리된다
DELETE d FROM schedule_job_detail d
  JOIN schedule_job j ON j.id = d.schedule_job_id
 WHERE j.job_class_name LIKE '%PlcConnectorSpyScheduleJob';
DELETE FROM schedule_job WHERE job_class_name LIKE '%PlcConnectorSpyScheduleJob';

-- 4) 스케줄러 이름 (application.yml scheduler.name.default 와 같게)
UPDATE schedule_job SET sched_name = 'TestScheduler' WHERE sched_name = 'NO_ASSIGNED';

-- 5) 값 형
UPDATE schedule_job_detail SET entry_value_type = LOWER(entry_value_type)
 WHERE entry_value_type IN ('STRING', 'INT', 'LONG', 'TEXT');
UPDATE schedule_job_detail SET entry_value_type = 'string' WHERE entry_value_type IS NULL OR entry_value_type IN ('', 'text');
