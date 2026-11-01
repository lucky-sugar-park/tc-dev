"""상태를 바꾸는 API 의 HTTP 메서드(결정 D5, 할 일 #9) — 예전 GET 은 405, 삭제는 DELETE·제어와 사용 변경은 POST.
없는 대상은 500 이 아니라 404(errorCode ERR_404)."""
import json
import os
import sys
import urllib.error
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402

TOKEN = common.admin_token()


def call_with_body(method, path, token=TOKEN):
    req = urllib.request.Request(common.BASE + path, method=method)
    if token:
        req.add_header("Authorization", token)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()


def call(method, path, token=TOKEN):
    return call_with_body(method, path, token)[0]


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
DELETES = [
    "/api/v1/ifsvc/management/delete/999999999",
    f"/api/v1/ifsvc/management/delete/byName/{NO}",
    "/api/v1/plc-connector/delete/999999999",
    f"/api/v1/plc-connector/delete/byName/{NO}",
    "/api/v1/schedule-job/management/delete/999999999",
    f"/api/v1/schedule-job/management/delete/byName/{NO}",
    "/api/v1/webhook/delete/999999999",
    f"/api/v1/webhook/delete/byName/{NO}",
]
POSTS = [
    "/api/v1/ifsvc/management/update/apply-use/999999999/true",
    *[f"/api/v1/plc-connector/control/{op}/{key}" for op in ("publish", "release", "pause", "resume")
      for key in ("999999999", f"byName/{NO}")],
    "/api/v1/plc-connector/test-connection/999999999",
    f"/api/v1/plc-connector/test-connection/byName/{NO}",
    "/api/v1/plc-connector/test-connection/byIpAddress/127.0.0.1/1",
    "/api/v1/schedule-job/monitoring/reloadAllScheduleJobs",
    "/api/v1/schedule-job/monitoring/pauseAllScheduleJobs",
    "/api/v1/schedule-job/monitoring/resumeAllScheduleJobs",
    f"/api/v1/schedule-job/monitoring/pauseScheduleJob/{NO}",
    f"/api/v1/schedule-job/monitoring/resumeScheduleJob/{NO}",
    f"/api/v1/schedule-job/monitoring/unscheduleJob/{NO}",
    f"/api/v1/schedule-job/monitoring/rescheduleJob/{NO}",
]

# 예전 방식(GET)은 막힌다 — 링크·미리 가져오기로 상태가 바뀌지 않는다
for p in DELETES + POSTS:
    s = call("GET", p)
    check(f"GET {p} → 405", s == 405, s)

# 바뀐 메서드는 컨트롤러까지 간다(401·403·405 가 아님). 경로를 틀린 404 는 본문에 errorCode 가 없고(ProblemDetail),
# 없는 대상의 404 는 errorCode ERR_404 다. 그 밖의 결과 코드(200 등)는 엔드포인트마다 다르다 — 500 은 없어야 한다
NOT_FOUND = set()
for method, paths in (("DELETE", DELETES), ("POST", POSTS)):
    for p in paths:
        if "pauseAll" in p or "resumeAll" in p:
            continue  # 다른 시험의 스케줄에 영향을 주지 않도록 실행하지 않는다(GET 405 만 확인)
        s, body = call_with_body(method, p)
        handled = s not in (401, 403, 405, 500) and (s != 404 or error_code(body) == "ERR_404")
        check(f"{method} {p} 는 처리됨({s})", handled, body[:200])
        if s == 404:
            NOT_FOUND.add(p)

# 없는 대상 → 404(예전에는 Optional.get()·null 로 500)
for p in [f"/api/v1/plc-connector/control/{op}/{key}" for op in ("publish", "release", "pause", "resume")
          for key in ("999999999", f"byName/{NO}")] + [
          "/api/v1/plc-connector/test-connection/999999999", f"/api/v1/plc-connector/test-connection/byName/{NO}",
          "/api/v1/ifsvc/management/update/apply-use/999999999/true"]:
    check(f"없는 대상 404: POST {p}", p in NOT_FOUND)
for p in ("/api/v1/ifsvc/management/find/999999999", "/api/v1/ifsvc/audit/find/999999999"):
    s, body = call_with_body("GET", p)
    check(f"없는 대상 404: GET {p}", s == 404 and error_code(body) == "ERR_404", (s, body[:200]))
s, body = call_with_body("GET", f"/api/v1/schedule-job/management/search/job-details/byName/{NO}")
check("없는 스케줄 작업의 상세 → 빈 목록(작업 ID 로 찾을 때와 같이)", s == 200 and json.loads(body) == [], (s, body[:200]))
s = call("DELETE", f"/api/v1/plc-connector/delete/byName/{NO}")
check("없는 PLC 커넥터 이름 삭제 → 200(ID 삭제와 같이)", s == 200, s)
s, body = call_with_body("POST", "/api/v1/plc-connector/control/no-such-op/1")
check("경로를 틀리면 404(errorCode 없음)", s == 404 and error_code(body) is None, (s, body[:200]))

# 권한 검사는 그대로 — 토큰 없이 401
check("토큰 없는 DELETE 401", call("DELETE", DELETES[1], token=None) == 401)

print(f"\n{sum(results)}/{len(results)} PASS")
sys.exit(0 if all(results) else 1)
