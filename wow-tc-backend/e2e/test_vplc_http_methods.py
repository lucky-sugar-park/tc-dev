"""vplc 의 상태를 바꾸는 API 메서드(tc #9 와 같이) — 예전 GET 은 405, 삭제는 DELETE·게시·해제·제어는 POST.
없는 가상 PLC 는 500 이 아니라 404(errorCode ERR_404). vplc 는 인증이 없다.
메모리(5-7) — 워드 값 쓰기(words)·낮은 바이트 부호, 이름으로 읽기, 게시 안 됨 409. vplc-e2e 의 D950~ 를 쓰고 0 으로 되돌린다."""
import json
import os
import sys
import urllib.error
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402

BASE = common.VPLC_BASE + "/api/v1/vplc"


def call(method, path):
    req = urllib.request.Request(BASE + path, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()


def post_json(path, body):
    req = urllib.request.Request(BASE + path, method="POST", data=json.dumps(body).encode(), headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, json.loads(r.read() or b"null")
    except urllib.error.HTTPError as e:
        return e.code, e.read()


def error_code(body):
    try:
        return json.loads(body).get("errorCode")
    except ValueError:
        return None


results = []


def check(name, cond, detail=""):
    results.append(bool(cond))
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else f"  -> {str(detail)[:300]}"), flush=True)


NO = "no-such-e2e-x"
KEYS = (NO, f"byName/{NO}")
DELETES = [f"/management/delete/{k}" for k in KEYS]
POSTS = [f"/management/{op}/{k}" for op in ("publish", "release") for k in KEYS] + \
        [f"/control/{op}/{k}" for op in ("start", "stop", "pause", "resume") for k in KEYS]

# 예전 방식(GET)은 막힌다
for p in DELETES + POSTS:
    s, _ = call("GET", p)
    check(f"GET {p} → 405", s == 405, s)

# 없는 가상 PLC — 게시·해제·제어는 404, 삭제는 200(없으면 할 일이 없음)
for p in POSTS:
    s, body = call("POST", p)
    check(f"POST {p} → 404 ERR_404", s == 404 and error_code(body) == "ERR_404", (s, body[:200]))
for p in DELETES:
    s, body = call("DELETE", p)
    check(f"DELETE {p} → 200", s == 200, (s, body[:200]))
for p in (f"/management/find/{NO}", f"/management/find/byName/{NO}"):
    s, body = call("GET", p)
    check(f"GET {p} → 404 ERR_404", s == 404 and error_code(body) == "ERR_404", (s, body[:200]))

# 조회는 GET 그대로
s, body = call("GET", "/management/search")
check("GET /management/search → 200", s == 200, (s, body[:200]))

# 메모리(5-7) — vplc-e2e D950~
MEM = {"vplcId": "vplc-e2e", "memoryType": "D*", "frameFormat": "BINARY"}
s, b = post_json("/manipulation/write", {**MEM, "startAddress": 950, "words": [1234, -1, 65535, 200]})
s2, r = post_json("/manipulation/read", {"vplcName": "vplc-e2e", "memoryType": "D*", "frameFormat": "BINARY", "startAddress": 950, "wordCount": 4})
check("메모리: 워드 값 쓰기 → 이름으로 읽기 1234·-1·-1·200(낮은 바이트 0x80 이상도 부호가 번지지 않음)",
      s == 200 and s2 == 200 and r["words"] == [1234, -1, -1, 200] and r["wordInfos"][0]["high"] == 4, (s, s2, r if s2 != 200 else r["words"]))
s, b = post_json("/manipulation/write", {**MEM, "startAddress": 950, "wordCount": 1, "data": "AB"})
check("메모리: 문자열 AB → 한 워드 0x4241(낮은 바이트가 앞 글자)", s == 200 and b["words"][0] == 0x4241, (s, b))
s, b = post_json("/manipulation/write", {**MEM, "startAddress": 950, "words": [70000]})
check("메모리: 범위 밖 워드 값 → 400 ERR_400", s == 400 and error_code(b) == "ERR_400", (s, b))
s, b = post_json("/manipulation/read", {**MEM, "vplcId": NO, "startAddress": 0, "wordCount": 1})
s2, b2 = post_json("/manipulation/read", {"vplcName": NO, "memoryType": "D*", "frameFormat": "BINARY", "startAddress": 0, "wordCount": 1})
check("메모리: 게시 안 된 아이디 409, 없는 이름 404(예전 500·null)", s == 409 and s2 == 404, (s, b, s2, b2))
post_json("/manipulation/write", {**MEM, "startAddress": 950, "words": [0, 0, 0, 0]})


# 메모리 능력 API(docs/vplc-architecture.md §2.4) — 종류와 관계없이 /memory
def put_json(path, body):
    req = urllib.request.Request(BASE + path, data=json.dumps(body).encode(), method="PUT")
    req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            return r.status, json.loads(r.read().decode() or "null")
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        return e.code, json.loads(raw) if raw.startswith("{") else raw


s, body = call("GET", "/memory/vplc-e2e")
desc = json.loads(body) if s == 200 else {}
d_area = next((a for a in desc.get("areas", []) if a.get("label") == "D"), {})
check("GET /memory/{MELSEC} → 영역 D(WORD, 16진 아님)", s == 200 and desc.get("kind") == "AREA" and d_area.get("unit") == "WORD"
      and d_area.get("hexAddress") is False and d_area.get("size", 0) > 960, (s, body[:300]))
s, b = put_json("/memory/vplc-e2e/area/D", {"start": 960, "values": [1234, -1, 0x4241]})
s2, r = call("GET", "/memory/vplc-e2e/area/D*?start=960&count=3")
check("영역 D 쓰기 → 읽기 1234·65535·0x4241(부호 없이)", s == 200 and s2 == 200 and json.loads(r)["values"] == [1234, 65535, 0x4241], (s, b, s2, r[:200]))
s, b = put_json("/memory/vplc-e2e/area/D", {"start": 960, "values": [70000]})
check("영역 범위 밖 값 → 400", s == 400 and isinstance(b, dict) and b.get("errorCode") == "ERR_400", (s, b))
s, b = call("GET", "/memory/vplc-e2e/area/D?start=0&count=3000")
check("한 번에 2000 칸 넘게 → 400", s == 400, (s, b[:200]))
s, b = call("GET", "/memory/vplc-e2e/tags")
check("MELSEC 에 태그 API → 400(영역 API 를 쓰라)", s == 400 and error_code(b) == "ERR_400", (s, b[:200]))
s, b = call("GET", f"/memory/{NO}/area/D?start=0&count=1")
check("없는 가상 PLC 메모리 → 404", s == 404, (s, b[:200]))
put_json("/memory/vplc-e2e/area/D", {"start": 960, "values": [0, 0, 0]})

# 상태 규칙 한 곳(AbstractVirtualPlc) — 실행 중인 PLC 재개·시작은 409
s, b = call("POST", "/control/resume/vplc-e2e")
check("RUNNING 에 재개 → 409", s == 409 and error_code(b) == "ERR_409", (s, b[:200]))
s, b = call("POST", "/control/start/vplc-e2e")
check("RUNNING 에 시작 → 409", s == 409 and error_code(b) == "ERR_409", (s, b[:200]))

# 같은 PC 의 연결 여러 개 — 각각 남고, 연결이 붙은 채 정지해도 200(docs/vplc-stop-error.md)
import socket  # noqa: E402
import time  # noqa: E402

TMP = "e2e-conn-key"
TMP_PORT = 15991
call("POST", f"/control/stop/byName/{TMP}")
call("POST", f"/management/release/byName/{TMP}")
call("DELETE", f"/management/delete/byName/{TMP}")
post_json("/management/register", {"name": TMP, "protocol": "MODBUS_TCP", "ipAddress": "127.0.0.1", "startPort": TMP_PORT,
                                   "published": False, "memoryTypeList": [], "settings": {"unitId": 1, "size": 10}})
s, raw = call("GET", f"/management/find/byName/{TMP}")
tmp_id = json.loads(raw)["id"]
call("POST", f"/management/publish/{tmp_id}")
call("POST", f"/control/start/{tmp_id}")
socks = [socket.create_connection(("127.0.0.1", TMP_PORT), timeout=5) for _ in range(2)]


def connected_clients():
    st, r = call("GET", f"/management/search/connection/{tmp_id}")
    conns = json.loads(r) if st == 200 else []
    return sum(len(c.get("connectedClientList") or []) for c in conns), sum(c.get("connectedCount", 0) for c in conns)


def wait_for(want):
    deadline = time.time() + 5
    while time.time() < deadline and connected_clients() != want:
        time.sleep(0.2)
    return connected_clients()


got = wait_for((2, 2))
check("같은 IP 연결 둘 → 연결 목록 2·연결 수 2(예전에는 한 줄로 겹쳤다)", got == (2, 2), got)
s, b = call("POST", f"/control/stop/{tmp_id}")
check("연결이 붙은 채 정지 → 200(예전 간헐적 500 — 끊김 이력 저장 실패가 정지까지 롤백)", s == 200, (s, b[:300]))
got = wait_for((0, 0))
check("정지 뒤 연결 목록 0·연결 수 0", got == (0, 0), got)
s, raw = call("GET", f"/management/find/{tmp_id}")
check("정지 상태가 DB 에 남음(STOPPED)", s == 200 and json.loads(raw).get("status") == "STOPPED", raw[:200])
for x in socks:
    x.close()
call("POST", f"/management/release/{tmp_id}")
call("DELETE", f"/management/delete/{tmp_id}")

print(f"\n{sum(results)}/{len(results)} PASS")
sys.exit(0 if all(results) else 1)
