"""PLC 소유권(다중화 3단계, docs/plc-ha-design.md §3.2·§3.3·§9.1 H1).

tc 를 TC_PLC_OWNERSHIP_ENABLED=true 로 띄웠을 때만 돈다(아니면 건너뜀). 인스턴스는 하나 — 다른 인스턴스는 Redis 키를 직접
써서 흉내 낸다(남의 임대, 살아 있는 다른 인스턴스).
전제: vplc plc-e2e 게시됨, plc-fake 게시됨(test_plc_adapter 가 만든 커넥터; 가짜 PLC 는 없어도 된다)."""
import json
import os
import sys
import time
import subprocess
import urllib.error
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402

TOKEN = common.admin_token()
OWNER = "tc:plc-owner:"


def redis(*args):
    return subprocess.run([common.REDIS_CLI, *args], capture_output=True, text=True).stdout.strip()


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
        raw = e.read().decode()
        try:
            return e.code, json.loads(raw)
        except ValueError:
            return e.code, raw


results = []


def check(name, cond, detail=""):
    results.append(bool(cond))
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else f"  -> {str(detail)[:300]}"), flush=True)


def wait(fn, seconds=8):
    deadline = time.time() + seconds
    while time.time() < deadline:
        v = fn()
        if v:
            return v
        time.sleep(0.3)
    return fn()


SEQ = [0]


def read():
    SEQ[0] += 1
    s, b = call("POST", "/api/v1/ifsvc/request-and-reply", {"transactionId": f"t-own-{int(time.time())}-{SEQ[0]}",
                                                           "messageId": f"m-{SEQ[0]}", "opcode": "plc.e2e.d100.read",
                                                           "sender": "e2e", "receiver": "tc", "data": {}})
    return s


def connection(name):
    return (call("GET", f"/api/v1/plc-connector/find/byName/{name}")[1] or {}).get("connection")


me = wait(lambda: redis("GET", OWNER + "plc-e2e"), 6)
if not me:
    print("SKIP 소유권 관리가 꺼져 있습니다(TC_PLC_OWNERSHIP_ENABLED=true 로 tc 를 띄우면 시험한다)")
    print("\n0/0 PASS")
    sys.exit(0)

# 1. 임대·인스턴스 목록
conn = connection("plc-e2e") or {}
check("plc-e2e 임대의 주인 = 연결한 인스턴스", conn.get("instance") == me, (me, conn))
check("임대 만료 10초 이하", 0 < int(redis("PTTL", OWNER + "plc-e2e") or -2) <= 10000)
check("인스턴스 목록에 자신(tc:instance:{id})", redis("EXISTS", f"tc:instance:{me}") == "1")
check("요청 200", read() == 200)
time.sleep(4)
check("3초마다 연장 — 만료가 다시 6초 넘게 남음", int(redis("PTTL", OWNER + "plc-e2e") or -2) > 6000)

# 2. 임대가 사라짐(FLUSHDB 등) → 연결을 닫지 않고 다시 잡는다(H1b)
connected_at = (connection("plc-e2e") or {}).get("lastConnectedAt")
redis("DEL", OWNER + "plc-e2e")
check("임대가 지워지면 다시 잡음(같은 인스턴스)", wait(lambda: redis("GET", OWNER + "plc-e2e") == me, 5))
check("그 사이 연결은 그대로(다시 연결하지 않음), 요청 200",
      (connection("plc-e2e") or {}).get("lastConnectedAt") == connected_at and read() == 200)

# 3. 다른 인스턴스가 임대를 가짐 → 강등(연결 닫음) → 그 인스턴스가 사라지면 이어받음
redis("SET", OWNER + "plc-e2e", "other-instance", "PX", "60000")
check("남의 임대를 보면 3초 안에 연결을 닫음", wait(lambda: connection("plc-e2e") is None, 5), connection("plc-e2e"))
check("주인이 아니면 요청 503(원격 전달은 4단계)", read() == 503)
redis("DEL", OWNER + "plc-e2e")
check("주인이 사라지면(임대 없음) 2초 검사에서 다시 잡고 연결", wait(lambda: redis("GET", OWNER + "plc-e2e") == me
                                                          and (connection("plc-e2e") or {}).get("activeChannels", 0) >= 1, 8))
check("이어받은 뒤 요청 200", wait(lambda: read() == 200, 6))

# 4. 해제 API → 임대 반납, 검사가 다시 잡지 않음 / 게시 API → 바로 임대·연결
s, _ = call("POST", "/api/v1/plc-connector/control/release/byName/plc-e2e")
check("해제 200, 임대 반납", s == 200 and redis("EXISTS", OWNER + "plc-e2e") == "0", s)
time.sleep(3)
check("해제된 커넥터는 검사가 다시 잡지 않음", redis("EXISTS", OWNER + "plc-e2e") == "0" and connection("plc-e2e") is None)
s, b = call("POST", "/api/v1/plc-connector/control/publish/byName/plc-e2e")
check("게시 200 → 바로 임대·연결, 요청 200", s == 200 and redis("GET", OWNER + "plc-e2e") == me and read() == 200, (s, b))

# 5. 균형 규칙 — 다른 인스턴스가 살아 있으면 한도 ceil(게시 수/2) 까지만 새로 잡는다. 자동 재균형은 없다(H4)
published = [c["name"] for c in call("GET", "/api/v1/plc-connector/search/all")[1] if c.get("status1") == "PUBLISHED"]
if len(published) == 2 and "plc-fake" in published:
    redis("SET", "tc:instance:fake-other", "-", "PX", "30000")
    time.sleep(2.5)
    check("다른 인스턴스가 늘어도 가진 임대는 그대로(자동 재균형 없음)",
          redis("GET", OWNER + "plc-e2e") == me and redis("GET", OWNER + "plc-fake") == me)
    redis("SET", OWNER + "plc-fake", "fake-other", "PX", "60000")
    wait(lambda: connection("plc-fake") is None, 5)
    redis("DEL", OWNER + "plc-fake")
    time.sleep(4.5)
    check("한도(2개/2대 = 1개)에 찼으면 주인 없는 plc-fake 를 잡지 않음", redis("EXISTS", OWNER + "plc-fake") == "0")
    redis("DEL", "tc:instance:fake-other")
    check("다른 인스턴스가 사라지면 한도가 늘어 plc-fake 를 잡음", wait(lambda: redis("GET", OWNER + "plc-fake") == me, 6))
else:
    print(f"SKIP 균형 규칙 — 게시 커넥터가 plc-e2e·plc-fake 두 개일 때만 본다: {published}")

print(f"\n{sum(results)}/{len(results)} PASS")
sys.exit(0 if all(results) else 1)
