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

print(f"\n{sum(results)}/{len(results)} PASS")
sys.exit(0 if all(results) else 1)
