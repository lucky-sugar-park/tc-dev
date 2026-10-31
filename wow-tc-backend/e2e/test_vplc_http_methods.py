"""vplc 의 상태를 바꾸는 API 메서드(tc #9 와 같이) — 예전 GET 은 405, 삭제는 DELETE·게시·해제·제어는 POST.
없는 가상 PLC 는 500 이 아니라 404(errorCode ERR_404). vplc 는 인증이 없다."""
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

print(f"\n{sum(results)}/{len(results)} PASS")
sys.exit(0 if all(results) else 1)
