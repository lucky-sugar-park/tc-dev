"""e2e 준비 — vplc 를 바라보는 PLC 커넥터(plc-e2e)와 읽기 인터페이스가 없으면 만들고 게시한다. 여러 번 돌려도 된다.

전제: vplc 가 떠 있고 127.0.0.1:15000 에 D 메모리를 가진 가상 PLC 가 실행 중이어야 한다.
"""
import json
import os
import sys
import time
import urllib.error
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402

R = common.HERE
TOKEN = common.admin_token()


def call(method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(common.BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    req.add_header("Authorization", TOKEN)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            raw = r.read().decode()
            return r.status, (json.loads(raw) if raw.strip().startswith(("{", "[")) else raw)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def exists(path):
    s, b = call("GET", path)
    return s == 200 and isinstance(b, dict) and b.get("name")


if not exists("/api/v1/plc-connector/find/byName/plc-e2e"):
    s, _ = call("POST", "/api/v1/plc-connector/register", json.load(open(os.path.join(R, "fixture-connector-e2e.json"), encoding="utf-8")))
    print("커넥터 plc-e2e 등록:", s)
for fixture in ("fixture-if-read.json", "fixture-if-read-reply.json"):
    d = json.load(open(os.path.join(R, fixture), encoding="utf-8"))
    if not exists(f"/api/v1/ifsvc/management/find/byName/{d['name']}"):
        s, _ = call("POST", "/api/v1/ifsvc/management/register", d)
        print("인터페이스 등록:", d["name"], s)
s, c = call("GET", "/api/v1/plc-connector/find/byName/plc-e2e")
if c.get("status1") != "PUBLISHED":
    s, _ = call("POST", "/api/v1/plc-connector/control/publish/byName/plc-e2e")
    print("plc-e2e 게시:", s)
    time.sleep(2)
s, b = call("POST", "/api/v1/ifsvc/request-and-reply", {"transactionId": f"t-setup-{int(time.time())}", "messageId": "m",
            "opcode": "plc.e2e.d100.read", "sender": "e2e", "receiver": "tc", "data": {}})
print("확인 요청(plc.e2e.d100.read):", s)
sys.exit(0 if s == 200 else 1)
