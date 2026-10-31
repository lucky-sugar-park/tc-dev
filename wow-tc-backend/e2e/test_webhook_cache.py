"""WebHook 캐시가 다른 인스턴스의 변경을 따라오는지(docs/plc-ha-design.md §9.1 H7).

다른 tc 인스턴스가 WebHook 을 바꾸거나 지운 것을 DB 를 직접 고쳐 흉내 낸다. 이 인스턴스의 캐시는 그 사이 옛 값을 쓰다가
만료(10초) 뒤 DB 의 값을 따른다.
전제: tc 를 TC_DELIVERY_ALLOWED_URL_PREFIXES=http://127.0.0.1:18090/ 로 띄움, vplc plc-e2e 게시됨."""
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
WH = f"e2e-whc-{RUN}"
IF_READ = "plc.e2e.d100.read"
CACHE_TTL = 10


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


def call(method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(common.BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    req.add_header("Authorization", TOKEN)
    try:
        with urllib.request.urlopen(req, timeout=40) as r:
            raw = r.read().decode()
            return r.status, (json.loads(raw) if raw.strip().startswith(("{", "[")) else raw)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


results = []


def check(name, cond, detail=""):
    results.append(bool(cond))
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else f"  -> {str(detail)[:300]}"), flush=True)


received = []


class H(BaseHTTPRequestHandler):
    def do_POST(self):
        self.rfile.read(int(self.headers.get("Content-Length") or 0))
        received.append(self.path)
        self.send_response(200)
        self.end_headers()

    def log_message(self, *a):
        pass


srv = HTTPServer(("127.0.0.1", 18090), H)
threading.Thread(target=srv.serve_forever, daemon=True).start()

SEQ = [0]


def read_and_wait(path):
    """PLC 읽기 한 번 → 이 시험 WebHook 의 받은 경로들(전달은 뒤에서 도므로 잠시 기다린다)"""
    SEQ[0] += 1
    before = len(received)
    s, b = call("POST", "/api/v1/ifsvc/request-and-reply", {"transactionId": f"t-whc-{RUN}-{SEQ[0]}", "messageId": f"m-{SEQ[0]}",
                                                           "opcode": IF_READ, "sender": "e2e", "receiver": "tc", "data": {}})
    deadline = time.time() + 5
    while time.time() < deadline and not [p for p in received[before:] if p.startswith(path)]:
        time.sleep(0.2)
    time.sleep(0.5)
    return s, [p for p in received[before:] if p.startswith(f"/whc/{RUN}/")]


s, b = call("POST", "/api/v1/webhook/register", {"name": WH, "url": f"http://127.0.0.1:18090/whc/{RUN}/a", "description": "e2e"})
check("준비: WebHook 등록", s in (200, 201), (s, b))
s, d = call("POST", "/api/v1/ifsvc/delivery", {"interfaceName": IF_READ, "deliveryType": "WEBHOOK", "targetName": WH})
check("준비: 전달 정의 201", s == 201, (s, d))
did = d.get("id") if isinstance(d, dict) else None

try:
    s, got = read_and_wait(f"/whc/{RUN}/a")
    check("처음 주소(a)로 전달", s == 200 and got == [f"/whc/{RUN}/a"], (s, got))

    # 다른 인스턴스가 주소를 바꿈
    sql(f"update WEBHOOK set URL='http://127.0.0.1:18090/whc/{RUN}/b' where NAME='{WH}'")
    changed_at = time.time()
    s, got = read_and_wait(f"/whc/{RUN}/a")
    check("바로 다음 전달은 캐시의 옛 주소(a) — 만료 전", got == [f"/whc/{RUN}/a"] and time.time() - changed_at < CACHE_TTL, got)
    time.sleep(max(0, CACHE_TTL + 0.5 - (time.time() - changed_at)))
    s, got = read_and_wait(f"/whc/{RUN}/b")
    check("만료 뒤에는 DB 의 새 주소(b)로 — 재기동 없이", got == [f"/whc/{RUN}/b"], got)

    # 다른 인스턴스가 지움
    sql(f"delete from WEBHOOK where NAME='{WH}'")
    time.sleep(CACHE_TTL + 0.5)
    s, got = read_and_wait(f"/whc/{RUN}/b")
    check("지워진 WebHook 으로는 만료 뒤 보내지 않음", s == 200 and got == [], got)
finally:
    if did:
        call("DELETE", f"/api/v1/ifsvc/delivery/{did}")
    call("DELETE", f"/api/v1/webhook/delete/byName/{WH}")
    srv.shutdown()

print(f"\n{sum(results)}/{len(results)} PASS")
sys.exit(0 if all(results) else 1)
