"""3단계(권한)·4단계(사용자) e2e. 시험용 사용자 비밀번호는 실행마다 만들고 출력하지 않는다."""
import json, os, re, secrets, subprocess, time, urllib.request, urllib.error
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402

R = os.path.dirname(os.path.abspath(__file__))
BASE = common.BASE
ADMIN = common.admin_token()
RUN = str(int(time.time()))[-6:]
LOCAL_YML = common.LOCAL_YML
MYSQL = common.MYSQL
MEMURAI = common.REDIS_CLI

def sql(q):
    pw = [re.match(r"\s*TC_DB_PASSWORD\s*:\s*(.*)", l).group(1).strip().strip('"').strip("'")
          for l in open(LOCAL_YML, encoding="utf-8") if re.match(r"\s*TC_DB_PASSWORD\s*:", l)][0]
    return subprocess.run([MYSQL, "-uroot", "-N", "-B", "tc", "-e", q], env=dict(os.environ, MYSQL_PWD=pw),
                          capture_output=True, text=True, encoding="utf-8", errors="replace").stdout.strip()

def call(method, path, body=None, token=None, headers=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", token)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            raw = r.read().decode()
            return r.status, (json.loads(raw) if raw.strip().startswith(("{", "[")) else raw), r.headers
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return e.code, json.loads(raw), e.headers
        except ValueError:
            return e.code, raw, e.headers

def login(uid, pw):
    s, b, h = call("POST", "/api/auth/login", {"id": uid, "password": pw})
    return s, (h.get("Authorization") if s == 200 else b), h.get("Set-Cookie", "")

results = []
def check(name, cond, detail=""):
    results.append(bool(cond))
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else f"  -> {str(detail)[:500]}"), flush=True)

def pw():
    return "Pw-" + secrets.token_urlsafe(12)

# 이전 실행의 로그인 실패 집계를 지운다(IP 집계는 사용자와 관계없이 쌓인다)
subprocess.run([MEMURAI, "DEL", "tc:login-block:ip:127.0.0.1", "tc:login-fail:ip:127.0.0.1"], capture_output=True)
op, vw, ad = f"op{RUN}", f"vw{RUN}", f"ad{RUN}"
pws = {op: pw(), vw: pw(), ad: pw()}

# ── 4단계: 사용자 생성·규칙 ──
s, b, _ = call("POST", "/api/v1/users", {"id": op, "name": "운영자", "userRoles": ["ROLE_OPERATOR"], "initialPassword": "short"}, ADMIN)
check("비밀번호 10자 미만 → 400", s == 400, (s, b))
same = f"SameId{RUN}xx"
s, b, _ = call("POST", "/api/v1/users", {"id": same, "name": "x", "userRoles": ["ROLE_VIEWER"], "initialPassword": same.lower()}, ADMIN)
check("아이디와 같은 비밀번호(대소문자 무시) → 400", s == 400, (s, b))
for uid, role in ((op, "ROLE_OPERATOR"), (vw, "ROLE_VIEWER"), (ad, "ROLE_ADMIN")):
    s, b, _ = call("POST", "/api/v1/users", {"id": uid, "name": uid, "userRoles": [role], "initialPassword": pws[uid]}, ADMIN)
    check(f"사용자 생성 {role} 201, 해시 없음, 변경 강제", s == 201 and b.get("passwordMustChange") is True and "password" not in b, (s, b))
s, b, _ = call("POST", "/api/v1/users", {"id": op, "name": "dup", "userRoles": ["ROLE_VIEWER"], "initialPassword": pw()}, ADMIN)
check("같은 아이디 409", s == 409, (s, b))

# 첫 로그인 → 변경 강제
tok = {}
for uid in (op, vw, ad):
    s, t, _ = login(uid, pws[uid])
    tok[uid] = t
    check(f"{uid[:2]} 로그인 200", s == 200, (s, t))
s, b, _ = call("GET", "/api/v1/ifsvc/delivery", token=tok[op])
check("비밀번호 변경 전 다른 API → 403 PASSWORD_CHANGE_REQUIRED", s == 403 and b.get("errorCode") == "ERR_403_TC_PASSWORD_CHANGE_REQUIRED", (s, b))
s, b, _ = call("GET", "/api/v1/me", token=tok[op])
check("비밀번호 변경 전에도 /api/v1/me 200", s == 200 and b.get("id") == op and b.get("passwordMustChange") is True, (s, b))
s, b, _ = call("PUT", "/api/v1/me/password", {"currentPassword": "wrong-password", "newPassword": pw()}, tok[op])
check("현재 비밀번호 틀림 → 400", s == 400, (s, b))
s, b, _ = call("PUT", "/api/v1/me/password", {"currentPassword": pws[op], "newPassword": pws[op]}, tok[op])
check("지금 비밀번호와 같은 새 비밀번호 → 400", s == 400, (s, b))
for uid in (op, vw, ad):
    new = pw()
    s, b, _ = call("PUT", "/api/v1/me/password", {"currentPassword": pws[uid], "newPassword": new}, tok[uid])
    check(f"{uid[:2]} 비밀번호 변경 200", s == 200, (s, b))
    pws[uid] = new
time.sleep(0.5)

# ── 3단계: 권한 ──
def st(method, path, who, body=None):
    return call(method, path, body, tok[who])[0]

check("운영자: 전달 정의 조회 200", st("GET", "/api/v1/ifsvc/delivery", op) == 200)
check("운영자: 커넥터 검색(POST R) 200", st("POST", "/api/v1/plc-connector/search", op, {"conditions": []}) == 200)
check("운영자: 인터페이스 사용 변경(POST U) 허용(200)", st("POST", "/api/v1/ifsvc/management/update/apply-use/" +
      sql("select id from interface where name='plc.fake.read'") + "/true", op) == 200)
check("운영자: 감사 삭제(DELETE D) 403", st("DELETE", "/api/v1/ifsvc/audit/delete/999999999", op) == 403)
check("운영자: 결과 재전송(관리자만) 403", st("POST", "/api/v1/ifsvc/redelivery/deliveries", op, {"auditIds": [1]}) == 403)
check("운영자: 사용자 목록 403", st("GET", "/api/v1/users", op) == 403)
check("운영자: API 클라이언트 목록 403", st("GET", "/api/v1/api-clients", op) == 403)
check("운영자: 설정 변경 이력 검색 200", st("POST", "/api/v1/config-history/search", op, {"size": 1}) == 200)
check("운영자: PLC 요청 200", call("POST", "/api/v1/ifsvc/request-and-reply", {"transactionId": f"t-u-{RUN}-1", "messageId": "m",
      "opcode": "plc.e2e.d100.read", "sender": "e2e", "receiver": "tc", "data": {}}, tok[op])[0] == 200)

check("조회자: 전달 정의 조회 200", st("GET", "/api/v1/ifsvc/delivery", vw) == 200)
check("조회자: 커넥터 검색(POST R) 200", st("POST", "/api/v1/plc-connector/search", vw, {"conditions": []}) == 200)
check("조회자: 커넥터 해제(POST X) 403", st("POST", "/api/v1/plc-connector/control/release/byName/no-such-plc", vw) == 403)
check("조회자: 인터페이스 삭제(DELETE D) 403", st("DELETE", "/api/v1/ifsvc/management/delete/999999999", vw) == 403)
check("조회자: 전달 정의 등록(POST C) 403", st("POST", "/api/v1/ifsvc/delivery", vw, {"interfaceName": "x", "deliveryType": "CALLBACK_URL"}) == 403)
check("조회자: 스케줄 전체 일시정지(POST X) 403", st("POST", "/api/v1/schedule-job/monitoring/pauseAllScheduleJobs", vw) == 403)
check("조회자: 권한 검사 모드 조회 403(관리자 전용)", st("GET", "/api/v1/security/permission-mode", vw) == 403)
s, b, _ = call("POST", "/api/v1/ifsvc/request-and-reply", {"transactionId": f"t-u-{RUN}-2", "messageId": "m",
      "opcode": "plc.e2e.d100.read", "sender": "e2e", "receiver": "tc", "data": {}}, tok[vw])
check("조회자: PLC 요청 403 (전환 끝 — 스위치 꺼짐)", s == 403, (s, b))
s, b, _ = call("PUT", "/api/v1/security/dispatch-any-user", {"on": True}, ADMIN)
check("관리자: 전환 스위치 켬(재기동 없음)", s == 200 and b.get("on") is True, (s, b))
s, b, _ = call("POST", "/api/v1/ifsvc/request-and-reply", {"transactionId": f"t-u-{RUN}-3", "messageId": "m",
      "opcode": "plc.e2e.d100.read", "sender": "e2e", "receiver": "tc", "data": {}}, tok[vw])
check("조회자: 스위치를 켜면 PLC 요청 200", s == 200, (s, b))
s, b, _ = call("PUT", "/api/v1/security/dispatch-any-user", {"on": None}, ADMIN)
check("관리자: 전환 스위치를 설정값(꺼짐)으로 되돌림", b.get("on") is False, (s, b))
time.sleep(5.5)  # 인스턴스별 5초 캐시
s, b, _ = call("POST", "/api/v1/ifsvc/request-and-reply", {"transactionId": f"t-u-{RUN}-4", "messageId": "m",
      "opcode": "plc.e2e.d100.read", "sender": "e2e", "receiver": "tc", "data": {}}, tok[vw])
check("조회자: 스위치를 끄면 다시 403", s == 403, (s, b))
h = call("GET", "/api/v1/config-history/OPTION/dispatch-any-user", None, ADMIN)[1]
check("전환 스위치 변경이 설정 변경 이력에 남음", len(h) >= 2, h[:2])
s, b, _ = call("POST", "/api/v1/users", {"id": f"cl{RUN}", "name": "x", "userRoles": ["ROLE_CLIENT"], "initialPassword": pw()}, ADMIN)
check("사용자에게 ROLE_CLIENT 는 줄 수 없음(400)", s == 400, (s, b))

check("관리자(새): 사용자 목록 200", st("GET", "/api/v1/users", ad) == 200)
check("관리자: 권한 검사 모드 AUDIT", call("GET", "/api/v1/security/permission-mode", None, ADMIN)[1].get("mode") == "AUDIT")
s, b, _ = call("PUT", "/api/v1/security/permission-mode", {"mode": "ENFORCE"}, ADMIN)
check("관리자: 모드 ENFORCE 로 변경", s == 200 and b.get("mode") == "ENFORCE", (s, b))
check("ENFORCE 에서도 선언된 API 는 그대로(운영자 전달 정의 조회 200)", st("GET", "/api/v1/ifsvc/delivery", op) == 200)
s, b, _ = call("PUT", "/api/v1/security/permission-mode", {"mode": None}, ADMIN)
check("관리자: 모드 설정값(AUDIT)으로 되돌림", b.get("mode") == "AUDIT", (s, b))
h = call("GET", "/api/v1/config-history/OPTION/permission-mode", None, ADMIN)[1]
check("모드 변경이 설정 변경 이력에 남음", len(h) >= 2 and h[0]["action"] == "UPDATE", h[:2])

# ── 4단계: 보호 규칙 ──
s, b, _ = call("POST", "/api/v1/users/tc-tester/disable", None, ADMIN)
check("자기 자신 사용 중지 → 409", s == 409, (s, b))
s, b, _ = call("DELETE", "/api/v1/users/tc-tester", None, ADMIN)
check("자기 자신 삭제 → 409", s == 409, (s, b))
s, b, _ = call("POST", f"/api/v1/users/{ad}/disable", None, ADMIN)
check("다른 관리자 사용 중지 200", s == 200 and b.get("enabled") is False, (s, b))
s, b, _ = call("PUT", "/api/v1/users/tc-tester", {"name": "tc tester", "userRoles": ["ROLE_VIEWER"]}, ADMIN)
check("마지막 활성 관리자의 관리자 역할 제거 → 409", s == 409, (s, b))
s, b, _ = call("GET", "/api/v1/users", None, tok[ad])
check("사용 중지된 관리자의 토큰 즉시 무효(401)", s == 401, (s, b))
s, _, _ = login(ad, pws[ad])
check("사용 중지된 사용자 로그인 401", s == 401)
call("POST", f"/api/v1/users/{ad}/enable", None, ADMIN)
s, t, _ = login(ad, pws[ad])
check("재개 후 로그인 200", s == 200, (s, t))

# 비밀번호 초기화 → 토큰 끊김 + 변경 강제
newpw = pw()
s, b, _ = call("POST", f"/api/v1/users/{op}/password-reset", {"newPassword": newpw}, ADMIN)
check("비밀번호 초기화 200", s == 200, (s, b))
s, b, _ = call("GET", "/api/v1/ifsvc/delivery", token=tok[op])
check("초기화 뒤 옛 토큰 401", s == 401, (s, b))
s, t, _ = login(op, newpw)
s2, b2, _ = call("GET", "/api/v1/ifsvc/delivery", token=t)
check("초기화 뒤 로그인은 되지만 변경 강제(403)", s == 200 and s2 == 403, (s, s2))

# ── 로그인 실패 제한 ──
s, b, _ = call("POST", "/api/auth/login", {"id": "no-such-user-" + RUN, "password": "x"})
check("없는 아이디는 401", s == 401, (s, b))
codes = [login(vw, "wrong-password-xx")[0] for _ in range(5)]
s, _, _ = login(vw, pws[vw])
check("틀린 비밀번호 5회 → 맞는 비밀번호도 429", codes == [401] * 5 and s == 429, (codes, s))
s, _, _ = login(ad, pws[ad])
check("다른 사용자는 같은 IP 에서도 로그인 가능(IP 한도 20)", s == 200, s)
subprocess.run([MEMURAI, "DEL", f"tc:login-block:user:{vw}", f"tc:login-fail:user:{vw}", "tc:login-block:ip:127.0.0.1",
                "tc:login-fail:ip:127.0.0.1"], capture_output=True)

# ── 이력 ──
opers = sql(f"select group_concat(distinct OPER order by OPER) from USER_HIST where USER_ID='{vw}'")
check("사용 이력: LOGIN·LOGIN_FAIL·LOGIN_THROTTLED·PASSWORD_CHANGE", all(x in opers for x in ("LOGIN", "LOGIN_FAIL", "LOGIN_THROTTLED", "PASSWORD_CHANGE")), opers)
h = call("GET", f"/api/v1/config-history/USER/{op}", None, ADMIN)[1]
txt = json.dumps(h, ensure_ascii=False)
check("설정 변경 이력: 사용자 CREATE·password-reset, 해시는 가림", any(x["action"] == "CREATE" for x in h)
      and any(x.get("detail") == "password-reset" for x in h) and "$2a$" not in txt and '\\"password\\":\\"***\\"' in txt, txt[:400])

# 정리: 시험 사용자 삭제(삭제 이력 확인)
for uid in (op, vw, ad):
    s, b, _ = call("DELETE", f"/api/v1/users/{uid}", None, ADMIN)
check("시험 사용자 삭제 204", s == 204, (s, b))
print(f"\n{sum(results)}/{len(results)} PASS")
sys.exit(0 if all(results) else 1)
