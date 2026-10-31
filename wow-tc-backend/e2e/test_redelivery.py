"""관리자 결과 재전송(#11) e2e — docs/redelivery-design.md.

전제: tc 를 TC_SECRET_KEY, TC_DELIVERY_ALLOWED_URL_PREFIXES=http://127.0.0.1:18090/ 로 띄움, vplc plc-e2e 게시됨.
plc.e2e.d100.read 에 이 시험의 전달 정의만 걸어 두고 끝에 지운다."""
import json
import os
import re
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, HTTPServer

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402

TOKEN = common.admin_token()
RUN = str(int(time.time()))
CONN = f"e2e-rd-{RUN}"
WH = f"e2e-rd-{RUN}"
TABLE = "e2e_redelivery"
UQ_TABLE = "e2e_redelivery_uq"
IF_READ = "plc.e2e.d100.read"
IF_FAKE = "plc.fake.read"


def db_password():
    for line in open(common.LOCAL_YML, encoding="utf-8"):
        m = re.match(r"\s*TC_DB_PASSWORD\s*:\s*(.*)", line)
        if m:
            return m.group(1).strip().strip('"').strip("'")
    raise SystemExit("application-local.yml 에 TC_DB_PASSWORD 가 없습니다")


DB_PW = db_password()


def sql(q):
    out = subprocess.run([common.MYSQL, "-uroot", "-N", "-B", "tc", "-e", q], env=dict(os.environ, MYSQL_PWD=DB_PW),
                         capture_output=True, text=True, encoding="utf-8", errors="replace")
    return out.stdout.strip()


def call(method, path, body=None, token=TOKEN):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(common.BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", token)
    try:
        with urllib.request.urlopen(req, timeout=40) as r:
            raw = r.read().decode()
            return r.status, (json.loads(raw) if raw.strip().startswith(("{", "[")) else raw)
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return e.code, json.loads(raw)
        except ValueError:
            return e.code, raw


results = []


def check(name, cond, detail=""):
    results.append(bool(cond))
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else f"  -> {str(detail)[:400]}"), flush=True)


def wait(fn, tries=20):
    for _ in range(tries):
        v = fn()
        if v:
            return v
        time.sleep(0.5)
    return fn()


def plc_read(opcode, n):
    tx = f"t-rd-{RUN}-{n}"
    s, b = call("POST", "/api/v1/ifsvc/request-and-reply", {"transactionId": tx, "messageId": f"m-{n}", "opcode": opcode,
                                                           "sender": "e2e", "receiver": "tc", "data": {}})
    return tx, s, b


def count(table, tx):
    return int(sql(f"select count(*) from {table} where TRANSACTION_ID='{tx}'") or 0)


def deliveries(tx, receiver_like):
    """[(ID, DESCRIPTION, MSG_JSON)] 오래된 순"""
    out = sql(f"select ID, DESCRIPTION, replace(replace(MSG_JSON, '\\n', ''), '\\t', '') from EXCHANGED_MESSAGE_AUDIT "
              f"where TRX_ID='{tx}' and MSG_TYPE='DELIVERY' and RECEIVER like '{receiver_like}' order by ID")
    return [line.split("\t") for line in out.splitlines()] if out else []


def redeliver(ids):
    return call("POST", "/api/v1/ifsvc/redelivery/deliveries", {"auditIds": ids})


# ── 받는 쪽 흉내: WebHook 서버 ──
received = []


class H(BaseHTTPRequestHandler):
    def do_POST(self):
        body = self.rfile.read(int(self.headers.get("Content-Length") or 0)).decode()
        received.append({"path": self.path, "headers": {k.lower(): v for k, v in self.headers.items()}, "body": body})
        self.send_response(200)
        self.end_headers()

    do_PUT = do_POST  # callback 주소(pushUrl)는 PUT

    def log_message(self, *a):
        pass


srv = HTTPServer(("127.0.0.1", 18090), H)
threading.Thread(target=srv.serve_forever, daemon=True).start()

# ── 준비: 표준 테이블 두 개(하나는 받는 쪽 유니크 키), DB 연결, WebHook, 전달 정의 ──
STD_COLS = """ID bigint auto_increment primary key, TRANSACTION_ID varchar(100), INTERFACE_NAME varchar(200), PLC_NAME varchar(200),
  SUCCESS boolean not null, ERROR_CODE varchar(100), ERROR_MESSAGE varchar(1000), COLLECTED_AT datetime(3) not null,
  PAYLOAD longtext, INSERTED_AT datetime(3) default current_timestamp(3)"""
sql(f"drop table if exists {TABLE}")
sql(f"drop table if exists {UQ_TABLE}")
sql(f"create table {TABLE} ({STD_COLS})")
sql(f"create table {UQ_TABLE} ({STD_COLS}, unique key UQ_E2E_RD (TRANSACTION_ID, INTERFACE_NAME))")

conn_body = {"name": CONN, "jdbcUrl": "jdbc:mariadb://localhost:3306/tc", "username": "root", "password": DB_PW,
             "maxPoolSize": 2, "description": "e2e redelivery"}
s, b = call("POST", "/api/v1/db-connections", conn_body)
if s == 400 and "TC_SECRET_KEY" in str(b):
    raise SystemExit("tc 를 TC_SECRET_KEY 와 함께 띄워야 합니다(e2e README)")
check("준비: DB 연결 등록 201", s == 201, (s, b))
s, b = call("POST", "/api/v1/webhook/register", {"name": WH, "url": f"http://127.0.0.1:18090/wh/{RUN}", "description": "e2e"})
check("준비: WebHook 등록", s in (200, 201), (s, b))

created = []
for body in (
        {"interfaceName": IF_READ, "deliveryType": "DB_TABLE", "targetName": CONN, "tableName": TABLE},
        {"interfaceName": IF_READ, "deliveryType": "DB_TABLE", "targetName": "tc", "tableName": UQ_TABLE},
        # 성공 응답은 보내지 않는 정의 — 응답 기록으로 보내기가 조건을 무시하는지 본다
        {"interfaceName": IF_READ, "deliveryType": "WEBHOOK", "targetName": WH, "deliverOn": "FAILURE"},
        {"interfaceName": IF_FAKE, "deliveryType": "DB_TABLE", "targetName": "tc", "tableName": TABLE}):
    s, b = call("POST", "/api/v1/ifsvc/delivery", body)
    if s == 201:
        created.append(b["id"])
check("준비: 전달 정의 4개 등록", len(created) == 4, created)

try:
    # ── 1. 연결을 끈 채 PLC 읽기 → 그 대상만 전달 실패 ──
    call("PUT", f"/api/v1/db-connections/{CONN}", dict(conn_body, password=None, use=False))
    tx1, s, b = plc_read(IF_READ, 1)
    check("PLC 읽기 200", s == 200 and b.get("success") is True, (s, b))
    original_data = b.get("data") if isinstance(b, dict) else None
    failed = wait(lambda: deliveries(tx1, f"DB_TABLE:{CONN}:%"))
    check("끈 연결로의 전달은 실패 감사", len(failed) == 1 and "사용 중지" in failed[0][1], failed)
    ok_uq = wait(lambda: deliveries(tx1, f"DB_TABLE:tc:{UQ_TABLE}"))
    check("tc 연결 전달은 성공(유니크 키 테이블 1행)", len(ok_uq) == 1 and ok_uq[0][1] == "OK" and count(UQ_TABLE, tx1) == 1, ok_uq)
    time.sleep(1)
    check("WebHook(FAILURE 만) 은 성공 응답을 받지 않음", not [r for r in received if tx1 in r["body"]], received)
    body1 = json.loads(failed[0][2]) if failed else {}
    check("DELIVERY 감사에 보낸 응답 전체가 남음(data 숫자 그대로, resultCode)",
          body1.get("response", {}).get("data") == original_data and "resultCode" in body1.get("response", {})
          and body1.get("redeliveryOf") is None, body1)
    fail_id = int(failed[0][0]) if failed else 0

    # ── 2. 아직 꺼진 연결로 재전송 → 거절 ──
    s, b = redeliver([fail_id])
    check("꺼진 연결로 재전송 → 400, 사유 사용 중지", s == 400 and b.get("accepted") == 0
          and "사용 중지" in str(b.get("rejected")), (s, b))

    # ── 3. 연결을 켜고 재전송 → 행이 들어가고 새 감사에 원본이 남음 ──
    call("PUT", f"/api/v1/db-connections/{CONN}", dict(conn_body, password=None, use=True))
    s, b = redeliver([fail_id])
    check("재전송 202, accepted 1", s == 202 and b.get("accepted") == 1 and not b.get("rejected"), (s, b))
    check("받는 쪽 테이블에 1행", wait(lambda: count(TABLE, tx1) == 1), count(TABLE, tx1))
    again = wait(lambda: len(deliveries(tx1, f"DB_TABLE:{CONN}:%")) == 2 and deliveries(tx1, f"DB_TABLE:{CONN}:%"))
    re_row = again[-1] if again else ["", "", "{}"]
    re_body = json.loads(re_row[2])
    check("재전송 감사: OK, redeliveryOf=원본", re_row[1] == "OK" and re_body.get("redeliveryOf") == fail_id, re_row)
    principal = sql(f"select PRINCIPAL from EXCHANGED_MESSAGE_AUDIT where ID={re_row[0] or 0}")
    check("재전송 감사의 주체는 요청한 사용자", principal.startswith("user:"), principal)
    payload, collected = (sql(f"select PAYLOAD, COLLECTED_AT from {TABLE} where TRANSACTION_ID='{tx1}'").split("\t") + ["", ""])[:2]
    check("행의 PAYLOAD 는 원래 응답 data", json.loads(payload or "{}") == original_data, payload)

    # ── 4. 같은 것을 곧바로 다시 → 중복 클릭으로 건너뜀 ──
    s, b = redeliver([fail_id])
    check("10초 안 같은 재전송 → 400, 방금 요청", s == 400 and "방금" in str(b.get("rejected")), (s, b))

    # ── 5. 성공한 전달도 다시 — 유니크 키에 걸리면 "이미 있음" 성공 ──
    s, b = redeliver([int(ok_uq[0][0])])
    check("성공한 전달 재전송 202", s == 202 and b.get("accepted") == 1, (s, b))
    uq = wait(lambda: len(deliveries(tx1, f"DB_TABLE:tc:{UQ_TABLE}")) == 2 and deliveries(tx1, f"DB_TABLE:tc:{UQ_TABLE}"))
    check("유니크 키 중복은 OK (이미 있음), 행은 그대로 1", bool(uq) and uq[-1][1].startswith("OK (이미 있음")
          and count(UQ_TABLE, tx1) == 1, uq)

    # ── 6. 보완 전 기록(MSG_JSON={}) 흉내 → 원본 RESPONSE(MSG_ID)에서 응답을 만든다 ──
    sql(f"update EXCHANGED_MESSAGE_AUDIT set MSG_JSON='{{}}' where ID={fail_id}")
    time.sleep(10.5)  # 중복 클릭 창이 지나기를 기다린다
    s, b = redeliver([fail_id])
    check("본문 없는 옛 전달 기록도 재전송 202", s == 202 and b.get("accepted") == 1, (s, b))
    check("받는 쪽 테이블에 행 하나 더(2)", wait(lambda: count(TABLE, tx1) == 2), count(TABLE, tx1))
    rows = sql(f"select PAYLOAD from {TABLE} where TRANSACTION_ID='{tx1}' order by ID").splitlines()
    check("옛 기록에서 만든 응답의 data 도 원래와 같다", len(rows) == 2 and json.loads(rows[1]) == original_data, rows)

    # ── 7. 응답 기록 → 고른 대상(전달 조건 무시), 재전송 헤더 ──
    resp_id = int(sql(f"select ID from EXCHANGED_MESSAGE_AUDIT where TRX_ID='{tx1}' and MSG_TYPE='RESPONSE'") or 0)
    s, b = call("POST", "/api/v1/ifsvc/redelivery/responses", {"auditIds": [resp_id], "targets": [
        {"deliveryType": "WEBHOOK", "targetName": WH},
        {"deliveryType": "WEBHOOK", "targetName": "no-such-hook"},
        {"deliveryType": "CALLBACK_URL", "targetName": "callback"}]})
    check("응답 기록 보내기 202 — 1건 보냄, 정의에 없는 대상·콜백은 거절", s == 202 and b.get("accepted") == 1
          and len(b.get("rejected", [])) == 2, (s, b))
    hit = wait(lambda: [r for r in received if tx1 in r["body"]])
    h = hit[0] if hit else {"headers": {}, "body": "{}", "path": ""}
    sent = json.loads(h["body"])
    check("WebHook 이 받음(성공 응답인데 FAILURE 정의 — 조건 무시)", bool(hit) and h["path"] == f"/wh/{RUN}", received)
    check("재전송 헤더 X-TC-Redelivery: true, X-TC-Redelivery-Of: 응답 감사 ID",
          h["headers"].get("x-tc-redelivery") == "true" and h["headers"].get("x-tc-redelivery-of") == str(resp_id), h["headers"])
    check("본문: 원래 transactionId·data, success=true", sent.get("transactionId") == tx1 and sent.get("data") == original_data
          and sent.get("success") is True, sent)

    # ── 7-1. callback 주소 — 비동기 요청은 REQUEST 감사에 pushUrl 을 남기고, 응답 기록으로 그 주소에 다시 보낸다 ──
    # test_paths 가 남겨 두는 CALLBACK_URL 정의가 있으면 그것을 쓴다(처음 전달도 callback 으로 간다)
    s, b = call("POST", "/api/v1/ifsvc/delivery", {"interfaceName": IF_READ, "deliveryType": "CALLBACK_URL", "deliverOn": "ALL"})
    check("준비: CALLBACK_URL 정의 있음(새로 201 또는 이미 있음 409)", s in (201, 409), (s, b))
    if s == 201:
        created.append(b["id"])
    cb_url = f"http://127.0.0.1:18090/cb/{RUN}"
    tx3 = f"t-rd-{RUN}-3"
    s, b = call("POST", "/api/v1/ifsvc/request", {"transactionId": tx3, "messageId": "m-3", "opcode": IF_READ, "sender": "e2e",
                                                  "receiver": "tc", "data": {}, "pushResult": True, "pushUrl": cb_url})
    check("비동기 요청 202", s == 202, (s, b))
    resp3 = wait(lambda: sql(f"select ID from EXCHANGED_MESSAGE_AUDIT where TRX_ID='{tx3}' and MSG_TYPE='RESPONSE'"))
    req3 = sql(f"select DESCRIPTION from EXCHANGED_MESSAGE_AUDIT where TRX_ID='{tx3}' and MSG_TYPE='REQUEST'")
    check("REQUEST 감사 DESCRIPTION = pushUrl: 주소", req3 == f"pushUrl: {cb_url}", req3)
    s, b = call("POST", "/api/v1/ifsvc/redelivery/responses", {"auditIds": [int(resp3 or 0)], "targets": [
        {"deliveryType": "CALLBACK_URL", "targetName": "callback"}]})
    check("응답 기록 → callback 202", s == 202 and b.get("accepted") == 1 and not b.get("rejected"), (s, b))
    cb = wait(lambda: [r for r in received if r["path"] == f"/cb/{RUN}" and r["headers"].get("x-tc-redelivery-of") == str(resp3)])
    cb0 = cb[0] if cb else {"headers": {}, "body": "{}"}
    check("callback 주소가 재전송을 받음(PUT, 재전송 헤더), 원래 transactionId",
          bool(cb) and json.loads(cb0["body"]).get("transactionId") == tx3, received)
    cb_audit = wait(lambda: [d for d in deliveries(tx3, f"CALLBACK_URL:callback:{cb_url}")
                             if json.loads(d[2]).get("redeliveryOf") == int(resp3 or 0)])
    check("callback 재전송 감사 OK, redeliveryOf=응답 감사 ID", bool(cb_audit) and cb_audit[0][1] == "OK", cb_audit)
    # 동기 요청은 callback 을 쓰지 않으므로 pushUrl 을 남기지 않는다
    tx4 = f"t-rd-{RUN}-4"
    s, b = call("POST", "/api/v1/ifsvc/request-and-reply", {"transactionId": tx4, "messageId": "m-4", "opcode": IF_READ,
                                                           "sender": "e2e", "receiver": "tc", "data": {},
                                                           "pushResult": True, "pushUrl": cb_url})
    req4 = sql(f"select DESCRIPTION from EXCHANGED_MESSAGE_AUDIT where TRX_ID='{tx4}' and MSG_TYPE='REQUEST'")
    check("동기 요청 200, REQUEST 감사에 pushUrl 없음", s == 200 and req4 == "", (s, req4))
    resp4 = int(sql(f"select ID from EXCHANGED_MESSAGE_AUDIT where TRX_ID='{tx4}' and MSG_TYPE='RESPONSE'") or 0)
    s, b = call("POST", "/api/v1/ifsvc/redelivery/responses", {"auditIds": [resp4], "targets": [
        {"deliveryType": "CALLBACK_URL", "targetName": "callback"}]})
    check("동기 요청의 응답 → callback 거절 400(주소 없음)", s == 400 and "pushUrl" in str(b.get("rejected")), (s, b))

    # ── 8. 디스패치 오류(ERROR) → 실패 응답으로 재전송 ──
    call("POST", "/api/v1/plc-connector/control/release/byName/plc-fake")
    tx2, s, b = plc_read(IF_FAKE, 2)
    call("POST", "/api/v1/plc-connector/control/publish/byName/plc-fake")
    check("가짜 PLC 해제 상태에서 읽기 실패", isinstance(b, dict) and b.get("success") is False, (s, b))
    d2 = wait(lambda: deliveries(tx2, f"DB_TABLE:tc:{TABLE}"))
    check("실패 응답도 전달됨(1행)", len(d2) == 1 and count(TABLE, tx2) == 1, d2)
    if d2:
        sql(f"update EXCHANGED_MESSAGE_AUDIT set MSG_JSON='{{}}' where ID={d2[0][0]}")
        s, b = redeliver([int(d2[0][0])])
        check("본문 없는 실패 전달 → ERROR 기록(거래 ID)에서 만들어 202", s == 202 and b.get("accepted") == 1, (s, b))
        check("실패 행 하나 더(SUCCESS=0, ERROR_CODE 같음)", wait(lambda: count(TABLE, tx2) == 2)
              and sql(f"select count(distinct ERROR_CODE) from {TABLE} where TRANSACTION_ID='{tx2}' and SUCCESS=0") == "1",
              sql(f"select SUCCESS, ERROR_CODE from {TABLE} where TRANSACTION_ID='{tx2}'"))
    err_id = int(sql(f"select ID from EXCHANGED_MESSAGE_AUDIT where TRX_ID='{tx2}' and MSG_TYPE='ERROR'") or 0)
    s, b = call("POST", "/api/v1/ifsvc/redelivery/responses",
                {"auditIds": [err_id], "targets": [{"deliveryType": "DB_TABLE", "targetName": "tc", "tableName": TABLE}]})
    check("ERROR 기록 → 고른 대상 202", s == 202 and b.get("accepted") == 1, (s, b))
    check("실패 행 하나 더(3)", wait(lambda: count(TABLE, tx2) == 3), count(TABLE, tx2))

    # ── 9. 잘못된 요청 ──
    s, b = redeliver([resp_id, 999999999999])
    reasons = str(b.get("rejected")) if isinstance(b, dict) else str(b)
    check("DELIVERY 아닌 기록·없는 기록 → 400, 사유 각각", s == 400 and "DELIVERY" in reasons and "없습니다" in reasons, (s, b))
    s, b = redeliver([])
    check("빈 목록 400", s == 400, (s, b))
    s, b = redeliver(list(range(1, 502)))
    check("501건 400(한 번에 500건)", s == 400 and "500" in str(b), (s, b))
    s, b = call("POST", "/api/v1/ifsvc/redelivery/responses", {"auditIds": [resp_id], "targets": []})
    check("응답 기록 보내기에 대상 없음 400", s == 400, (s, b))
    s, b = call("POST", "/api/v1/ifsvc/redelivery/deliveries", {"auditIds": [fail_id]}, token=None)
    check("토큰 없이 401", s == 401, s)
finally:
    for d in created:
        call("DELETE", f"/api/v1/ifsvc/delivery/{d}")

# ── 10. 지워진 WebHook 으로는 다시 보내지 않는다 ──
wh_delivery = sql(f"select ID from EXCHANGED_MESSAGE_AUDIT where TRX_ID='{tx1}' and MSG_TYPE='DELIVERY' and RECEIVER like 'WEBHOOK:{WH}:%' limit 1")
call("DELETE", f"/api/v1/webhook/delete/byName/{WH}")
s, b = redeliver([int(wh_delivery or 0)])
check("지워진 WebHook 전달 기록 재전송 → 400, WebHook 없음", s == 400 and "WebHook" in str(b.get("rejected")), (s, b))

s, b = call("DELETE", f"/api/v1/db-connections/{CONN}")
check("정리: DB 연결 삭제 204", s == 204, (s, b))
sql(f"drop table if exists {TABLE}")
sql(f"drop table if exists {UQ_TABLE}")
srv.shutdown()

print(f"\n{sum(results)}/{len(results)} PASS")
sys.exit(0 if all(results) else 1)
