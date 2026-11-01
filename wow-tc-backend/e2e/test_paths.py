"""미검증 경로 e2e — WRITE, CALLBACK_URL·허용 목록, 커넥터 해제·게시(스케줄 Job 은 test_schedule_jobs.py).
전제: tc 를 TC_DELIVERY_ALLOWED_URL_PREFIXES=http://127.0.0.1:18090/ 로 띄움, vplc plc-e2e 게시됨."""
import json, os, re, subprocess, sys, threading, time, urllib.request, urllib.error
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402
from http.server import BaseHTTPRequestHandler, HTTPServer

R = os.path.dirname(os.path.abspath(__file__))
BASE = common.BASE
TOKEN = common.admin_token()
LOCAL_YML = common.LOCAL_YML
MYSQL = common.MYSQL
RUN = str(int(time.time()))

def sql(q):
    pw = [re.match(r"\s*TC_DB_PASSWORD\s*:\s*(.*)", l).group(1).strip().strip('"').strip("'")
          for l in open(LOCAL_YML, encoding="utf-8") if re.match(r"\s*TC_DB_PASSWORD\s*:", l)][0]
    out = subprocess.run([MYSQL, "-uroot", "-N", "-B", "tc", "-e", q], env=dict(os.environ, MYSQL_PWD=pw), capture_output=True, text=True, encoding="utf-8", errors="replace")
    return out.stdout.strip()

def call(method, path, body=None, timeout=40):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    req.add_header("Authorization", TOKEN)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
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
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else f"  -> {detail}"), flush=True)

seq = [0]
def plc(path, opcode, data=None, **extra):
    seq[0] += 1
    body = {"transactionId": f"t-path-{RUN}-{seq[0]}", "messageId": f"m-{seq[0]}", "opcode": opcode,
            "sender": "e2e", "receiver": "tc", "data": data or {}}
    body.update(extra)
    return body["transactionId"], call("POST", path, body)

# ── 콜백 수신기 ──
received = []
class H(BaseHTTPRequestHandler):
    def do_PUT(self):
        n = int(self.headers.get("Content-Length", 0))
        received.append((self.path, json.loads(self.rfile.read(n) or b"{}")))
        self.send_response(200); self.end_headers()
    do_POST = do_PUT
    def log_message(self, *a):
        pass
srv = HTTPServer(("127.0.0.1", 18090), H)
threading.Thread(target=srv.serve_forever, daemon=True).start()

# ── 준비: WRITE 인터페이스 ──
s, b = call("GET", "/api/v1/ifsvc/management/find/byName/plc.e2e.d100.write")
if s != 200 or not isinstance(b, dict) or not b.get("name"):
    d = common.d100_interface("plc.e2e.d100.write", "WRITE", description="e2e write D100..D101")
    s, b = call("POST", "/api/v1/ifsvc/management/register", d)
    print("interface register", d["name"], s, "" if s == 200 else b)

# ── 1. WRITE ──
v1, v2 = int(RUN) % 30000, -1234
tx, (s, b) = plc("/api/v1/ifsvc/request-and-reply", "plc.e2e.d100.write", {"d100": v1, "d101": v2})
check("WRITE 200·success", s == 200 and b.get("success") is True, (s, b))
tx, (s, b) = plc("/api/v1/ifsvc/request-and-reply", "plc.e2e.d100.read")
check(f"WRITE 뒤 READ 값 일치(d100={v1}, d101={v2})", s == 200 and b["data"].get("d100") == v1 and b["data"].get("d101") == v2, (s, b))
tx, (s, b) = plc("/api/v1/ifsvc/request-and-reply", "plc.e2e.d100.write", {"d100": 1})
check("WRITE 데이터 누락 → 실패(4xx/5xx)", s >= 400, (s, b))
tx, (s, b) = plc("/api/v1/ifsvc/request-and-reply", "plc.e2e.d100.write", {"d100": 40000, "d101": 1})
check("WRITE INT16 범위 초과 → 400", s == 400, (s, b))
tx, (s, b) = plc("/api/v1/ifsvc/request-and-reply", "plc.e2e.d100.write", {"d100": "77", "d101": 1.0})
check("WRITE 문자열 숫자·정수형 실수 허용 → 200", s == 200 and b.get("success") is True, (s, b))

# ── 2. CALLBACK_URL ──
# 이 인터페이스에는 WEBHOOK 전달 정의가 있어 정의 기반으로 동작한다 — 요청 callback 을 쓰려면 CALLBACK_URL 정의가 있어야 한다
s, d = call("POST", "/api/v1/ifsvc/delivery", {"interfaceName": "plc.e2e.d100.read", "deliveryType": "CALLBACK_URL",
                                             "deliverOn": "ALL", "description": "e2e callback"})
cb_def = d.get("id") if s == 201 else None
print("callback definition", s, cb_def)
tx, (s, b) = plc("/api/v1/ifsvc/request", "plc.e2e.d100.read", pushResult=True, pushUrl="http://127.0.0.1:18090/cb/ok")
time.sleep(3)
got = [body for path, body in received if path == "/cb/ok" and body.get("transactionId") == tx]
check("CALLBACK 허용 주소 → PUT 수신, 결과 본문", s == 202 and got and got[0].get("success") is True, (s, received))
tx2, (s, b) = plc("/api/v1/ifsvc/request", "plc.e2e.d100.read", pushResult=True, pushUrl="http://127.0.0.1:18091/cb/no")
time.sleep(3)
desc = sql(f"select DESCRIPTION from EXCHANGED_MESSAGE_AUDIT where TRX_ID='{tx2}' and MSG_TYPE='DELIVERY'")
check("CALLBACK 허용 목록 밖 → 보내지 않고 DELIVERY 감사에 실패 기록", s == 202 and desc and desc != "OK", (s, desc))
ok_desc = sql(f"select DESCRIPTION from EXCHANGED_MESSAGE_AUDIT where TRX_ID='{tx}' and MSG_TYPE='DELIVERY' and RECEIVER like 'CALLBACK_URL%'")
check("CALLBACK 성공 DELIVERY 감사 OK", ok_desc == "OK", ok_desc)
if cb_def:
    call("DELETE", f"/api/v1/ifsvc/delivery/{cb_def}")

# 스케줄 Job 은 test_schedule_jobs.py

# ── 4. 커넥터 해제·게시 ──
s, b = call("POST", "/api/v1/plc-connector/control/release/byName/plc-e2e")
time.sleep(1)
s2, c = call("GET", "/api/v1/plc-connector/find/byName/plc-e2e")
tx, (s3, b3) = plc("/api/v1/ifsvc/request-and-reply", "plc.e2e.d100.read")
check("해제 → RELEASED, 요청 503", s == 200 and c.get("status1") == "RELEASED" and s3 == 503, (s, c.get("status1"), s3))
s, b = call("POST", "/api/v1/plc-connector/control/publish/byName/plc-e2e")
time.sleep(1)
s2, c = call("GET", "/api/v1/plc-connector/find/byName/plc-e2e")
tx, (s3, b3) = plc("/api/v1/ifsvc/request-and-reply", "plc.e2e.d100.read")
check("해제 직후 다시 게시 → PUBLISHED, 요청 200 (vplc 슬롯이 바로 비었음)", c.get("status1") == "PUBLISHED" and s3 == 200, (c.get("status1"), s3, b3))

srv.shutdown()
print(f"\n{sum(results)}/{len(results)} PASS")
sys.exit(0 if all(results) else 1)
