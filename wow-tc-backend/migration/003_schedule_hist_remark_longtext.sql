-- 003 스케줄 실행 이력의 REMARK 를 LONGTEXT 로 넓힌다 (2026-10-30)
--
-- 성공한 실행은 REMARK 에 PLC 응답 전체가 들어간다. VARCHAR(255) 였을 때는 "Data too long for column 'remark'" 로
-- 저장이 실패해 성공 이력이 하나도 남지 않았다(실패 이력만 남음). ddl-auto=update 는 칼럼 형을 바꾸지 않으므로 이 스크립트를 돌린다.
-- 넓히기만 하므로 값은 바뀌지 않는다. 대상 DB: tc

SET SESSION lock_wait_timeout = 10;

ALTER TABLE schedule_job_exec_hist MODIFY REMARK LONGTEXT NULL;
