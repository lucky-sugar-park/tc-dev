"""framework-core 0.8.46 반영 확인 — 공개 경로 설정, 검색 조건 처리 (docs/framework-core-0.8.46-changes.md)."""
import json
import os
import sys
import urllib.error
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402

TOKEN = common.admin_token()


def call(method, path, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(common.BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", token)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
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


# 공개 경로 — tc 목록(로그인·재발급·헬스)만 열리고, framework 기본값(MES 기능)은 tc 에서 닫힌다
s, b = call("GET", "/api/health")
check("GET /api/health 인증 없이 200", s == 200 and b.get("status") in ("UP", "DEGRADED"), (s, b))
s, b = call("POST", "/api/auth/login", {"id": "no-such-user-x", "password": "x"})
check("POST /api/auth/login 은 공개(인증 없이 들어가 401 로그인 실패)", s == 401 and b.get("errorCode") == "ERR_401", (s, b))
for method, path in (("POST", "/api/auth/signup"), ("GET", "/api/auth/verify")):
    s, b = call(method, path)
    check(f"{method} {path} 는 tc 에서 공개 아님(401)", s == 401, (s, b))
# 화면 문구는 5단계(새 프론트)부터 tc 도 공개 — 로그인 화면이 먼저 부른다(docs/frontend-design.md §4.1)
s, b = call("GET", "/api/i18n/messages")
check("GET /api/i18n/messages 는 공개(로그인 화면용, 5단계부터)", s == 200 and isinstance(b, dict), s)

# 검색 — conjType 이 없어도 되고(AND), 없는 항목은 400(예전에는 조건을 빼고 전체 행을 돌려줬다)
s, b = call("POST", "/api/v1/plc-connector/search", {"conditions": [{"name": "name", "condType": "EQUALS", "value": "plc-e2e"}]}, TOKEN)
check("conjType 없는 검색 200, 조건대로 걸러짐", s == 200 and isinstance(b, list) and [c.get("name") for c in b] == ["plc-e2e"], (s, b))
s, b = call("POST", "/api/v1/plc-connector/search", {"conditions": [{"name": "noSuchField", "condType": "EQUALS", "value": "x"}]}, TOKEN)
check("없는 검색 항목 → 400(전체 행을 돌려주지 않음)", s == 400 and b.get("errorCode") == "ERR_400", (s, b))
s, b = call("POST", "/api/v1/plc-connector/search", {}, TOKEN)
check("조건 없는 검색 200", s == 200 and isinstance(b, list), (s, b))

print(f"\n{sum(results)}/{len(results)} PASS")
sys.exit(0 if all(results) else 1)
