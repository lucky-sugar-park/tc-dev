-- 012 인터페이스 옛 칼럼 정리 (2026-11-01, docs/interfaces.md)
--
-- RAW 를 없앤 뒤(011) 저장만 되고 읽는 곳이 없던 인터페이스 칼럼을 지운다.
-- - SYNC: 동기/비동기는 부르는 쪽이 고르는 API 가 정한다(/request-and-reply 동기, /request 비동기).
-- - REPLY: 응답 인터페이스로 회신하던 RAW 의 것.
-- - WEB_HOOK_LIST: 결과를 WebHook 으로 보내는 것은 전달 정의(INTERFACE_DELIVERY, WEBHOOK)가 한다.
-- 화면 문구도 지운다(씨앗에서 뺀 키). ddl-auto=update 는 칼럼을 지우지 않으므로 이 스크립트를 돌려야 한다.
-- 다시 돌려도 안전하다. 대상 DB: tc

SET SESSION lock_wait_timeout = 10;

ALTER TABLE interface DROP COLUMN IF EXISTS sync;
ALTER TABLE interface DROP COLUMN IF EXISTS reply;
ALTER TABLE interface DROP COLUMN IF EXISTS web_hook_list;

DELETE FROM i18n_item
 WHERE code IN ('i18n.tc.interface.sync', 'i18n.tc.interface.reply', 'i18n.tc.interface.webhookHelp');

-- 확인만(바꾸지 않음): 없는 인터페이스를 가리키는 스케줄 Job — 011 이 지운 RAW opcode 등. 나오면 스케줄 화면에서 고치거나 지운다.
SELECT d.schedule_job_id, d.entry_value AS interface_name
  FROM schedule_job_detail d
 WHERE d.entry_key = 'interfaceName'
   AND NOT EXISTS (SELECT 1 FROM interface i WHERE i.name = d.entry_value);
