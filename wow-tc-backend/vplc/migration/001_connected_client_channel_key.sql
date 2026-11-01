-- vplc 001 — 옛 붙어 있는 연결 표(connected_client) 정리 (docs/vplc-stop-error.md §4)
--
-- 붙어 있는 연결은 이제 vplc_connected_client 표에 (vplc_id, port, client_channel_id) 키로 남는다 — vplc(ddl-auto update)가
-- 새 표를 스스로 만들므로 이 파일을 실행하지 않아도 동작한다. 옛 표 connected_client 는 키가 (vplc_id, port, client_ip_address)라
-- 같은 PC 의 연결 여러 개를 한 줄로 겹쳤고(정지 500 의 원인), 이제 쓰지 않는다 — 지워도 된다.
--
-- vplc DB(application.yml 의 VPLC_DB_URL, 기본 tc)에 실행한다. vplc 가 떠 있어도 된다.

DROP TABLE IF EXISTS connected_client;
