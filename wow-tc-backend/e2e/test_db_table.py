"""DB_TABLE 결과 전달(할 일 #10, docs/db-table-delivery.md) — 외부 DB 연결 등록·검증, 결과 행 기록(성공·실패), 연결 삭제 보호.
전제: tc 를 TC_SECRET_KEY 와 함께 띄움, vplc plc-e2e 게시됨(setup_e2e.py). 시험 DB 는 로컬 tc DB 를 '외부 DB' 로 다시 등록해 쓴다."""
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402

TOKEN = common.admin_token()
RUN = str(int(time.time()))
CONN = f"e2e-db-{RUN}"
TABLE = "e2e_plc_result"
BAD_TABLE = "e2e_plc_result_bad"


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
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else f"  -> {str(detail)[:300]}"), flush=True)


def plc_read(opcode, n):
    tx = f"t-db-{RUN}-{n}"
    s, b = call("POST", "/api/v1/ifsvc/request-and-reply", {"transactionId": tx, "messageId": f"m-{n}", "opcode": opcode,
                                                           "sender": "e2e", "receiver": "tc", "data": {}})
    return tx, s, b


def rows(tx):
    """(success, error_code, plc_name, payload) — 전달은 비동기라 잠시 기다린다"""
    for _ in range(20):
        out = sql(f"select SUCCESS, ifnull(ERROR_CODE,''), ifnull(PLC_NAME,''), PAYLOAD from {TABLE} where TRANSACTION_ID='{tx}' order by ID")
        if out:
            return [line.split("\t") for line in out.splitlines()]
        time.sleep(0.5)
    return []


# 표준 테이블(대상 시스템이 만든다는 가정) + 칼럼이 빠진 테이블
STD_DDL = f"""create table if not exists {TABLE} (
  ID bigint auto_increment primary key, TRANSACTION_ID varchar(100), INTERFACE_NAME varchar(200), PLC_NAME varchar(200),
  SUCCESS boolean not null, ERROR_CODE varchar(100), ERROR_MESSAGE varchar(1000), COLLECTED_AT datetime(3) not null,
  PAYLOAD longtext, INSERTED_AT datetime(3) default current_timestamp(3))"""
sql(STD_DDL)
sql(f"create table if not exists {BAD_TABLE} (ID bigint auto_increment primary key, TRANSACTION_ID varchar(100), PAYLOAD longtext)")

# ── DB 연결 등록 ──
conn_body = {"name": CONN, "jdbcUrl": "jdbc:mariadb://localhost:3306/tc", "username": "root", "password": DB_PW,
             "maxPoolSize": 2, "description": "e2e"}
s, b = call("POST", "/api/v1/db-connections", conn_body)
if s == 400 and "TC_SECRET_KEY" in str(b):
    raise SystemExit("tc 를 TC_SECRET_KEY 와 함께 띄워야 합니다(e2e README)")
check("DB 연결 등록 201", s == 201, (s, b))
check("응답에 비밀번호 없음, passwordSet=true", s == 201 and "password" not in b and b.get("passwordSet") is True, b)
stored = sql(f"select ENCRYPTED_PASSWORD from DB_CONNECTION where NAME='{CONN}'")
check("DB 에는 암호화된 값만(v1:…, 평문 아님)", stored.startswith("v1:") and DB_PW not in stored, stored[:8])
s, b = call("GET", f"/api/v1/config-history/DB_CONNECTION/{CONN}")
check("설정 변경 이력에 비밀번호 평문 없음", s == 200 and DB_PW not in json.dumps(b, ensure_ascii=False), (s, str(b)[:200]))

s, b = call("POST", "/api/v1/db-connections", dict(conn_body, name=CONN))
check("같은 이름 409", s == 409, (s, b))
s, b = call("POST", "/api/v1/db-connections", dict(conn_body, name="tc"))
check("예약 이름 'tc' 400", s == 400, (s, b))
s, b = call("POST", "/api/v1/db-connections", dict(conn_body, name=CONN + "-x", jdbcUrl="jdbc:mariadb://localhost:3306/tc?allowLocalInfile=true"))
check("금지 옵션(allowLocalInfile) 400", s == 400, (s, b))
s, b = call("POST", "/api/v1/db-connections", dict(conn_body, name=CONN + "-x", jdbcUrl="jdbc:h2:mem:x"))
check("허용되지 않은 드라이버 400", s == 400, (s, b))
s, b = call("POST", "/api/v1/db-connections", conn_body, token=None)
check("토큰 없이 401", s == 401, s)

s, b = call("POST", f"/api/v1/db-connections/{CONN}/test")
check("연결 시험 성공", s == 200 and b.get("success") is True, (s, b))
s, b = call("POST", "/api/v1/db-connections/tc/test")
check("tc 자체 DB 연결 시험 성공", s == 200 and b.get("success") is True, (s, b))
s, b = call("PUT", f"/api/v1/db-connections/{CONN}", dict(conn_body, password=None, description="e2e-upd"))
check("비밀번호 없이 수정 → 기존 비밀번호 유지", s == 200 and b.get("passwordSet") is True
      and sql(f"select ENCRYPTED_PASSWORD from DB_CONNECTION where NAME='{CONN}'") == stored, (s, b))
s, b = call("POST", f"/api/v1/db-connections/{CONN}/test")
check("수정 뒤에도 연결 시험 성공", s == 200 and b.get("success") is True, (s, b))

# ── 전달 정의 검증 ──
def deliv(opcode, conn, table, **extra):
    body = {"interfaceName": opcode, "deliveryType": "DB_TABLE", "targetName": conn, "tableName": table}
    body.update(extra)
    return call("POST", "/api/v1/ifsvc/delivery", body)


s, b = deliv("plc.e2e.d100.read", CONN, "no_such_table_e2e")
check("없는 테이블 400", s == 400, (s, b))
s, b = deliv("plc.e2e.d100.read", CONN, BAD_TABLE)
check("표준 칼럼이 빠진 테이블 400(빠진 칼럼 안내)", s == 400 and "PLC_NAME" in str(b), (s, b))
s, b = deliv("plc.e2e.d100.read", CONN, "x; drop table y")
check("테이블 이름 모양이 아니면 400", s == 400, (s, b))
s, b = deliv("plc.e2e.d100.read", "no-such-conn", TABLE)
check("없는 DB 연결 404", s == 404, (s, b))
s, b = deliv("plc.e2e.d100.read", CONN, None)
check("테이블 이름 없으면 400", s == 400, (s, b))

# 기존 전달 정의가 있으면 그 정의만 쓰므로, 이 시험 인터페이스의 정의를 확인해 두고 끝에 지운다
created = []
s, b = deliv("plc.e2e.d100.read", CONN, TABLE)
check("DB_TABLE 전달 정의 등록 201", s == 201, (s, b))
if s == 201:
    created.append(b["id"])
s, b = deliv("plc.e2e.d100.read", "tc", TABLE)
check("tc 자체 DB 로 DB_TABLE 정의 등록 201", s == 201, (s, b))
if s == 201:
    created.append(b["id"])

# ── 성공 결과 기록 ──
tx, s, b = plc_read("plc.e2e.d100.read", 1)
check("PLC 읽기 200", s == 200 and b.get("success") is True, (s, b))
r = rows(tx)
check("두 정의 모두 한 행씩 기록(외부 연결 + tc)", len(r) == 2, r)
if r:
    succ, err, plc_name, payload = r[0]
    data = json.loads(payload) if payload.startswith("{") else {}
    check("SUCCESS=1, PLC_NAME=plc-e2e", succ == "1" and plc_name == "plc-e2e", r[0])
    check("PAYLOAD 는 응답 data 와 같은 JSON", isinstance(b, dict) and data == b.get("data"), (payload, b.get("data") if isinstance(b, dict) else b))
audit = sql(f"select count(*) from EXCHANGED_MESSAGE_AUDIT where TRX_ID='{tx}' and MSG_TYPE='DELIVERY' and RECEIVER like 'DB_TABLE:%' and DESCRIPTION='OK'")
check("전달 감사 2건(DB_TABLE, OK)", audit == "2", audit)

# ── 실패 결과 기록 — 가짜 PLC 커넥터를 잠시 해제해 확실히 실패시킨다(앞 시험의 가짜 PLC 가 살아 있을 수 있다) ──
s, b = deliv("plc.fake.read", CONN, TABLE)
check("실패 기록용 정의 등록 201", s == 201, (s, b))
if s == 201:
    created.append(b["id"])
    call("POST", "/api/v1/plc-connector/control/release/byName/plc-fake")
    tx, s, b = plc_read("plc.fake.read", 2)
    r = rows(tx)
    check("PLC 실패 응답도 한 행(SUCCESS=0, ERROR_CODE 있음)", len(r) == 1 and r[0][0] == "0" and r[0][1] != "", (s, b, r))
    call("POST", "/api/v1/plc-connector/control/publish/byName/plc-fake")

# ── 연결 사용 중지 → 기록 실패가 감사에 남음 ──
s, b = call("PUT", f"/api/v1/db-connections/{CONN}", dict(conn_body, password=None, use=False))
check("DB 연결 사용 중지 200", s == 200 and b.get("use") is False, (s, b))
tx, s, b = plc_read("plc.e2e.d100.read", 3)
time.sleep(2)
r = rows(tx)
check("중지된 연결은 기록 안 됨, tc 연결은 기록(1행)", len(r) == 1, r)
fail = sql(f"select DESCRIPTION from EXCHANGED_MESSAGE_AUDIT where TRX_ID='{tx}' and MSG_TYPE='DELIVERY' and RECEIVER like 'DB_TABLE:{CONN}:%'")
check("중지된 연결의 전달 실패가 감사에 남음", "사용 중지" in fail, fail)
call("PUT", f"/api/v1/db-connections/{CONN}", dict(conn_body, password=None, use=True))

# ── 삭제 보호와 정리 ──
s, b = call("DELETE", f"/api/v1/db-connections/{CONN}")
check("쓰는 전달 정의가 있으면 삭제 409", s == 409, (s, b))
for d in created:
    call("DELETE", f"/api/v1/ifsvc/delivery/{d}")
s, b = call("DELETE", f"/api/v1/db-connections/{CONN}")
check("정의를 지운 뒤 삭제 204", s == 204, (s, b))
s, b = call("GET", f"/api/v1/db-connections/{CONN}")
check("삭제 뒤 조회 404", s == 404, (s, b))
sql(f"drop table if exists {BAD_TABLE}")

print(f"\n{sum(results)}/{len(results)} PASS")
sys.exit(0 if all(results) else 1)
