"""PLC 어댑터 결함 수정 e2e — 가짜 PLC(:15100, DELAY=2, CLOSE_AFTER=5, 5초 지연), tc PLC 시간 초과 3초 전제."""
import json, os, sys, threading, time, urllib.request, urllib.error
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402

R = os.path.dirname(os.path.abspath(__file__))
FAKE_LOG = os.path.join(R, "fake.out.log")
BASE = common.BASE
TOKEN = common.admin_token()
PHASE = sys.argv[1] if len(sys.argv) > 1 else "main"

def call(method, path, body=None, timeout=30):
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
def read(opcode="plc.fake.read"):
    seq[0] += 1
    t0 = time.time()
    s, b = call("POST", "/api/v1/ifsvc/request-and-reply",
                {"transactionId": f"t-plc-{int(t0)}-{seq[0]}", "messageId": f"m-{seq[0]}", "opcode": opcode,
                 "sender": "e2e", "receiver": "tc", "data": {}})
    return s, b, time.time() - t0

def setup():
    s, b = call("GET", "/api/v1/plc-connector/find/byName/plc-fake")
    if s != 200 or not isinstance(b, dict) or not b.get("name"):
        s, base = call("GET", "/api/v1/plc-connector/find/byName/plc-e2e")
        info = {k: v for k, v in base.items() if k not in ("id", "createdBy", "createdDate", "createdDateAsString",
                                                              "updatedBy", "updatedDate", "updatedDateAsString")}
        info.update({"name": "plc-fake", "ports": [15100], "description": "fake PLC for adapter tests",
                     "published": False, "status1": "REGISTERED", "status2": "STOPPED"})
        s, b = call("POST", "/api/v1/plc-connector/register", info)
        print("connector register", s, b if s != 200 else "")
        for src, name, reply in (("if-req.json", "plc.fake.read", "plc.fake.read.reply"), ("if-rep.json", "plc.fake.read.reply", None)):
            d = json.load(open(os.path.join(R, src), encoding="utf-8"))
            d["name"] = name
            d["plcName"] = "plc-fake"
            if reply:
                d["replyName"] = reply
            s, b = call("POST", "/api/v1/ifsvc/management/register", d)
            print("interface register", name, s, "" if s == 200 else b)
    s, b = call("POST", "/api/v1/plc-connector/control/publish/byName/plc-fake")
    print("publish", s)
    time.sleep(2)

if PHASE == "main":
    setup()
    # 1. 기본
    s, b, dt = read()
    check("요청 #1 → 200, 값 1", s == 200 and b["data"].get("d100") == 1, (s, b))
    # 2. 늦은 응답 — #2 는 5초 늦게 오고 tc 는 3초에 포기한다
    s, b, dt = read()
    check("요청 #2 → 504 (약 3초)", s == 504 and 2.5 < dt < 4.5, (s, dt, b))
    # 3. 바로 다음 요청 — 예전 코드라면 #2 의 늦은 응답(값 2)을 받았다
    s, b, dt = read()
    check("요청 #3 → 200, 값 3 (늦은 응답 2가 섞이지 않음)", s == 200 and b["data"].get("d100") == 3, (s, dt, b))
    time.sleep(3)  # #2 의 늦은 응답이 도착할 시간
    s, b, dt = read()
    check("요청 #4 → 200, 값 4", s == 200 and b["data"].get("d100") == 4, (s, b))
    # 4. 연결 끊김 — #5 에 답한 뒤 가짜 PLC 가 연결을 끊는다
    s, b, dt = read()
    check("요청 #5 → 200, 값 5", s == 200 and b["data"].get("d100") == 5, (s, b))
    time.sleep(4)
    s, b, dt = read()
    check("끊긴 뒤 자동 재연결 → 요청 #6 200, 값 6", s == 200 and b["data"].get("d100") == 6, (s, b))
    s, b = call("GET", "/api/v1/plc-connector/find/byName/plc-fake")
    check("재연결 동안 커넥터는 PUBLISHED 유지", b.get("status1") == "PUBLISHED", (b.get("status1"), b.get("status2")))
    # 5. 동시 요청 — 채널 1개에서 직렬화되어 값이 섞이지 않는다
    out = []
    def fire():
        s, b, dt = read()
        out.append((s, b.get("data", {}).get("d100"), b.get("data", {}).get("d101")))
    ts = [threading.Thread(target=fire) for _ in range(6)]
    [t.start() for t in ts]; [t.join() for t in ts]
    vals = [v for s, v, w in out if s == 200]
    check("동시 6건 모두 200, 값이 서로 다르고 d100==d101", len(vals) == 6 and len(set(vals)) == 6 and all(v == w for _, v, w in out), out)
    # 6. 게시 멱등 — 다시 게시해도 새 연결을 열지 않는다
    before = open(FAKE_LOG, encoding="utf-8", errors="replace").read().count("거부")
    s, b = call("POST", "/api/v1/plc-connector/control/publish/byName/plc-fake")
    time.sleep(2)
    after = open(FAKE_LOG, encoding="utf-8", errors="replace").read().count("거부")
    s, b, dt = read()
    check("게시를 다시 해도 연결 추가 없음(거부 로그 없음) + 요청 200", after == before and s == 200, (before, after, s))
    # 7. 기존 vplc 커넥터도 그대로
    s, b, dt = read("plc.e2e.d100.read")
    check("vplc 커넥터(plc-e2e) 요청 200", s == 200 and b.get("success") is True, (s, b))

elif PHASE == "after-restart":
    for name in ("plc-fake", "plc-e2e"):
        s, b = call("GET", f"/api/v1/plc-connector/find/byName/{name}")
        check(f"재기동 뒤 {name} PUBLISHED", b.get("status1") == "PUBLISHED", (b.get("status1"), b.get("status2")))
    s, b, dt = read()
    check("재기동 뒤 plc-fake 요청 200", s == 200, (s, b))
    s, b, dt = read("plc.e2e.d100.read")
    check("재기동 뒤 plc-e2e 요청 200", s == 200, (s, b))

elif PHASE == "plc-down":
    s, b, dt = read()
    check("PLC 가 꺼진 채 기동 → 앱은 떠 있고 요청은 503", s == 503, (s, b))

elif PHASE == "plc-up":
    s, b, dt = read()
    check("PLC 가 뒤늦게 켜지면 자동 연결 → 요청 200", s == 200, (s, b))

print(f"\n{sum(results)}/{len(results)} PASS")
sys.exit(0 if all(results) else 1)
