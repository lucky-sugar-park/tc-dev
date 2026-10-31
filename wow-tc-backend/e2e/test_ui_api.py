"""화면(새 프론트) 백엔드 계약 — docs/frontend-design.md §4 (PLC 일반화 5단계 5-1).

- 로그인·재발급 본문 accessToken, 내 권한(역할별 + 화면 권한 uiroute.tc.*), 셸 빈 응답, 내 정보·비밀번호.
- 다국어(로그인 전 공개, 4개 언어, 관리), 공통 코드(LOV·관리·하위 있으면 409), 북마크(본인만, 중복 409 ERR_409_EXISTS).
- SSE(/api/sse — 연결 공개, 구독 권한, audit_message 수신), 홈 요약, 시험 읽기, 프로토콜 설명.
- 화면 서빙: TC_E2E_UI_DIR(tc 를 띄운 TC_UI_DIR 과 같은 폴더)이 있을 때만 — SPA 경로·자산 캐시·API 404.
전제: vplc plc-e2e 게시됨(setup_e2e.py). 시험 사용자는 만들고 지운다(비밀번호는 출력하지 않음)."""
import http.cookies
import json
import os
import secrets
import sys
import threading
import time
import urllib.error
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402

BASE = common.BASE
ADMIN = common.admin_token()
RUN = str(int(time.time()))[-6:]


def call(method, path, body=None, token=None, headers=None, raw=False):
    data = json.dumps(body).encode() if body is not None and not raw else (body.encode() if raw else None)
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "text/plain" if raw else "application/json")
    if token:
        req.add_header("Authorization", token)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            text = r.read().decode()
            return r.status, (json.loads(text) if text.strip().startswith(("{", "[")) else text), r.headers
    except urllib.error.HTTPError as e:
        text = e.read().decode()
        try:
            return e.code, json.loads(text), e.headers
        except ValueError:
            return e.code, text, e.headers


results = []


def check(name, cond, detail=""):
    results.append(bool(cond))
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else f"  -> {str(detail)[:400]}"), flush=True)


def login(uid, pw):
    s, b, h = call("POST", "/api/auth/login", {"id": uid, "password": pw})
    return s, b, h


users = []
try:
    # 1. 로그인·재발급 — 본문 accessToken(헤더와 같음)
    uid, pw = common._admin_credentials()
    s, b, h = login(uid, pw)
    header = h.get("Authorization", "") if s == 200 else ""
    check("로그인 본문 accessToken = Authorization 헤더 토큰, passwordChangeRequired 있음",
          s == 200 and b.get("accessToken") and header == "Bearer " + b["accessToken"] and "passwordChangeRequired" in b.get("userInfo", {}),
          (s, list(b) if isinstance(b, dict) else b))
    cookie = http.cookies.SimpleCookie(h.get("Set-Cookie", ""))
    refresh = "; ".join(f"{k}={v.value}" for k, v in cookie.items())
    s, b2, h2 = call("POST", "/api/auth/reissue", headers={"Cookie": refresh})
    check("재발급 본문 accessToken = 헤더 토큰", s == 200 and b2.get("accessToken") and h2.get("Authorization") == "Bearer " + b2["accessToken"],
          (s, b2))

    # 2. 권한 — 관리자 *, 조회자·운영자는 역할 표 + 화면 권한
    s, perms, _ = call("GET", "/api/me/permissions", token=ADMIN)
    check("관리자 권한 {*: ACRUDX}", s == 200 and perms == {"*": "ACRUDX"}, (s, perms))
    tok = {}
    passwords = {}
    for role in ("ROLE_VIEWER", "ROLE_OPERATOR"):
        u, p = f"ui{role[5:7].lower()}{RUN}", "Pw-" + secrets.token_urlsafe(12)
        s, b, _ = call("POST", "/api/v1/users", {"id": u, "name": u, "userRoles": [role], "initialPassword": p}, ADMIN)
        users.append(u)
        passwords[role] = p
        s2, b2, h2 = login(u, p)
        tok[role] = h2.get("Authorization") if s2 == 200 else None
        check(f"{role} 만들고 로그인(비밀번호 변경 필요 상태)", s == 201 and s2 == 200 and b2["userInfo"].get("passwordChangeRequired") is True, (s, s2))
    s, vp, _ = call("GET", "/api/me/permissions", token=tok["ROLE_VIEWER"])
    check("조회자 권한: 커넥터 화면 AR, 인터페이스 시험·사용자 화면 없음, 홈 있음, 비밀번호 변경 전에도 200",
          s == 200 and vp.get("uiroute.tc.plc.connector") == "AR" and vp.get("tc.plc") == "R" and "uiroute.tc.plc.interface-test" not in vp
          and "uiroute.tc.admin.user" not in vp and "uiroute.tc.home" in vp, (s, vp))
    s, op, _ = call("GET", "/api/me/permissions", token=tok["ROLE_OPERATOR"])
    check("운영자 권한: 커넥터 ACRUDX, 인터페이스 시험 AX, 감사 AR, 관리 화면 없음",
          s == 200 and op.get("uiroute.tc.plc.connector") == "ACRUDX" and op.get("uiroute.tc.plc.interface-test") == "AX"
          and op.get("uiroute.tc.ops.audit") == "AR" and "uiroute.tc.admin.apiclient" not in op, (s, op))

    # 3. 셸 빈 응답·내 정보·비밀번호
    ok = all(call("GET", p, token=ADMIN)[1] == v for p, v in (("/api/menus", []), ("/api/ui-routes", []),
                                                                ("/api/operation-options/effective?ids=a,b", {})))
    check("셸: /api/menus·/api/ui-routes 빈 목록, 운영 옵션 빈 값", ok)
    s, b, _ = call("GET", "/api/me/profile", token=tok["ROLE_VIEWER"])
    check("내 정보(비밀번호 변경 전에도) 200", s == 200 and b.get("id") == users[0], (s, b))
    s, b, _ = call("POST", "/api/me/password", {"currentPassword": "wrong-" + RUN, "newPassword": "Pw-" + secrets.token_urlsafe(12)},
                   token=tok["ROLE_VIEWER"])
    check("비밀번호 변경(POST) 현재 비밀번호 틀림 → 4xx", 400 <= s < 500, (s, b))
    s, b, _ = call("GET", "/api/common-codes/lov?parent=common.code.ui.tab_user_pref", token=tok["ROLE_VIEWER"])
    check("비밀번호 변경 전에는 LOV 도 403(변경 강제)", s == 403 and b.get("errorCode") == "ERR_403_TC_PASSWORD_CHANGE_REQUIRED", (s, b))
    changed = []
    for role in ("ROLE_VIEWER", "ROLE_OPERATOR"):
        s, b, _ = call("POST", "/api/me/password", {"currentPassword": passwords[role], "newPassword": "Pw-" + secrets.token_urlsafe(14)},
                       token=tok[role])
        changed.append(s)
    check("비밀번호 변경(POST) 200 — 이후 같은 토큰으로 계속", changed == [200, 200], changed)

    # 4. 다국어 — 로그인 전 공개, 4개 언어
    vals = {lang: call("GET", f"/api/i18n/messages?lang={lang}")[1] for lang in ("ko", "EN", "JA", "zh")}
    check("다국어(토큰 없이): 메뉴 '커넥터' 4개 언어, 셸 문구도",
          vals["ko"].get("i18n.menu.tc.plc.connector") == "커넥터" and vals["EN"].get("i18n.menu.tc.plc.connector") == "Connectors"
          and vals["JA"].get("i18n.menu.tc.plc.connector") == "コネクタ" and vals["zh"].get("i18n.menu.tc.plc.connector") == "连接器"
          and vals["ko"].get("i18n.auth.backToLogin") == "로그인으로", {k: len(v) if isinstance(v, dict) else v for k, v in vals.items()})
    s, b, _ = call("GET", "/api/i18n/messages?lang=FR")
    check("모르는 언어 → 400", s == 400, (s, b))
    code = f"i18n.e2e.ui{RUN}"
    s, b, _ = call("PUT", f"/api/i18n/items/{code}", {"kind": "common.code.i18n.kind.label",
                                                    "values": {"KO": "시험", "EN": "Test", "JA": "テスト", "ZH": "测试"}}, ADMIN)
    s2, en, _ = call("GET", "/api/i18n/messages?lang=EN")
    check("다국어 관리: 저장 → 그 언어 문구에 나옴", s == 200 and b.get("values", {}).get("JA") == "テスト" and en.get(code) == "Test", (s, b))
    s, b, _ = call("GET", f"/api/i18n/items?q=ui{RUN}", token=ADMIN)
    check("다국어 관리: 검색", s == 200 and len(b) == 1 and b[0]["code"] == code, (s, b))
    s, b, _ = call("PUT", f"/api/i18n/items/{code}", {"values": {"KO": "x"}}, tok["ROLE_OPERATOR"])
    check("다국어 관리: 운영자 403", s == 403, (s, b))
    s, b, _ = call("DELETE", f"/api/i18n/items/{code}", token=ADMIN)
    check("다국어 관리: 지우기 204, 문구에서 사라짐", s == 204 and code not in call("GET", "/api/i18n/messages?lang=EN")[1], (s, b))

    # 5. 공통 코드
    s, lov, _ = call("GET", "/api/common-codes/lov?parent=common.code.ui.tab_user_pref", token=tok["ROLE_VIEWER"])
    check("LOV: 탭 유지 선택 3개, i18nCode", s == 200 and [x["code"].rsplit(".", 1)[1] for x in lov] == ["follow", "light", "retain"]
          and lov[0]["i18nCode"] == "i18n." + lov[0]["code"] and lov[0]["isUse"] is True, (s, lov))
    s, b, _ = call("GET", "/api/common-codes/lov?parent=common.code.ui.tab_user_pref")
    check("LOV: 토큰 없으면 401", s == 401, (s, b))
    g = f"common.code.e2e{RUN}"
    s1, _, _ = call("POST", "/api/common-codes", {"code": g, "name": "시험 그룹"}, ADMIN)
    s2, _, _ = call("POST", "/api/common-codes", {"code": g + ".a", "parent": g, "name": "가"}, ADMIN)
    s3, b3, _ = call("POST", "/api/common-codes", {"code": g + ".a", "parent": g, "name": "가"}, ADMIN)
    s4, lv, _ = call("GET", f"/api/common-codes/lov?parent={g}", token=ADMIN)
    s5, b5, _ = call("DELETE", f"/api/common-codes/{g}", token=ADMIN)
    check("공통 코드: 만들기 201, 같은 코드 409, LOV 에 나옴, 하위 있으면 지우기 409",
          s1 == 201 and s2 == 201 and s3 == 409 and [x["name"] for x in lv] == ["가"] and s5 == 409, (s1, s2, s3, b3, lv, s5, b5))
    s6, _, _ = call("PUT", f"/api/common-codes/{g}.a", {"name": "가나", "isUse": False}, ADMIN)
    s7, lv, _ = call("GET", f"/api/common-codes/lov?parent={g}", token=ADMIN)
    s8, _, _ = call("DELETE", f"/api/common-codes/{g}.a", token=ADMIN)
    s9, _, _ = call("DELETE", f"/api/common-codes/{g}", token=ADMIN)
    check("공통 코드: 쓰지 않음 → LOV 에서 빠짐, 지우기 204", s6 == 200 and lv == [] and s8 == 204 and s9 == 204, (s6, lv, s8, s9))

    # 6. 북마크 — 본인만
    vt, ot = tok["ROLE_VIEWER"], tok["ROLE_OPERATOR"]
    bm = {"name": "내 커넥터", "pathName": "/plc/connectors", "description": "e2e", "state": {"screenKey": "tc.plc.connector", "v": "1"}}
    s1, b1, _ = call("POST", "/api/me/bookmarks", bm, vt)
    s2, b2, _ = call("POST", "/api/me/bookmarks", bm, vt)
    s3, l3, _ = call("GET", "/api/me/bookmarks", token=vt)
    s4, l4, _ = call("GET", "/api/me/bookmarks", token=ot)
    check("북마크: 더하기 201, 같은 이름 409 ERR_409_EXISTS, 본인 목록에만",
          s1 == 201 and b1.get("userId") == users[0] and s2 == 409 and b2.get("errorCode") == "ERR_409_EXISTS"
          and [x["name"] for x in l3] == ["내 커넥터"] and l3[0]["state"] == bm["state"] and l4 == [], (s1, b1, s2, b2, l3, l4))
    s5, b5, _ = call("PUT", "/api/me/bookmarks?name=" + urllib.request.quote("내 커넥터"), dict(bm, description="고침"), vt)
    s6, _, _ = call("DELETE", "/api/me/bookmarks?name=" + urllib.request.quote("내 커넥터"), token=ot)
    s7, _, _ = call("DELETE", "/api/me/bookmarks?name=" + urllib.request.quote("내 커넥터"), token=vt)
    check("북마크: 고치기 200, 남의 것 지우기 404, 지우기 204", s5 == 200 and b5.get("description") == "고침" and s6 == 404 and s7 == 204, (s5, s6, s7))

    # 7. SSE — 연결은 공개, 구독 권한, audit_message 받기
    got = []
    cid = f"e2e-{RUN}"

    def listen():
        try:
            with urllib.request.urlopen(BASE + f"/api/sse/connect/{cid}", timeout=20) as r:
                end = time.time() + 15
                while time.time() < end:
                    line = r.readline().decode("utf-8", "replace")
                    if not line:
                        break
                    got.append(line.strip())
                    if line.startswith("event:audit_message"):
                        break
        except Exception as e:  # noqa: BLE001
            got.append("ERR " + str(e))

    t = threading.Thread(target=listen, daemon=True)
    t.start()
    time.sleep(1.5)
    s1, _, _ = call("POST", f"/api/sse/event/add/{cid}", "audit_message", vt, raw=True)
    s2, _, _ = call("POST", f"/api/sse/event/add/{cid}", "apikey.expiring", vt, raw=True)
    s3, _, _ = call("POST", f"/api/sse/event/add/{cid}", "plc_connection", vt, raw=True)
    check("SSE 구독: 조회자 audit_message·plc_connection 200, API 키 알림 403", s1 == 200 and s3 == 200 and s2 == 403, (s1, s2, s3))
    call("POST", "/api/v1/ifsvc/request-and-reply", {"transactionId": f"t-ui-{RUN}", "messageId": "m", "opcode": "plc.e2e.d100.read",
                                                     "sender": "e2e", "receiver": "tc", "data": {}}, ADMIN)
    t.join(16)
    check("SSE: 연결(토큰 없이) 뒤 구독한 audit_message 를 받음", any(x.startswith("event:audit_message") for x in got), got[:6])

    # 7-1. 송수신 기록 검색(화면용 /search/query) — 기간·거래 ID·종류·실패만
    since = time.strftime("%Y-%m-%dT%H:%M:%S", time.gmtime(time.time() - 600)) + "Z"
    s, q, _ = call("POST", "/api/v1/ifsvc/audit/search/query", {"transactionId": f"t-ui-{RUN}", "from": since}, tok["ROLE_VIEWER"])
    check("기록 검색: 거래 하나의 요청·응답(조회자도)", s == 200 and sorted({r["messageType"] for r in q["items"]}) == ["REQUEST", "RESPONSE"]
          and q["total"] == len(q["items"]), (s, q if s != 200 else [r["messageType"] for r in q["items"]]))
    s, q, _ = call("POST", "/api/v1/ifsvc/audit/search/query", {"failedOnly": True, "size": 20}, ADMIN)
    check("기록 검색: 실패만 — 오류·ERR 응답·OK 아닌 전달", s == 200 and all(
        r["messageType"] == "ERROR" or (r["messageType"] == "RESPONSE" and (r.get("description") or "").startswith("ERR"))
        or (r["messageType"] == "DELIVERY" and not (r.get("description") or "").startswith("OK")) for r in q["items"]), (s, q))
    s, q, _ = call("POST", "/api/v1/ifsvc/audit/search/query", {"messageTypes": ["REQUEST"], "opcode": "plc.e2e", "size": 5}, ADMIN)
    check("기록 검색: 종류·opcode, 최신순·쪽 크기", s == 200 and len(q["items"]) <= 5 and all(r["messageType"] == "REQUEST" and "plc.e2e" in r["name"] for r in q["items"])
          and [r["id"] for r in q["items"]] == sorted([r["id"] for r in q["items"]], reverse=True), (s, q))
    s, q, _ = call("POST", "/api/v1/ifsvc/audit/search/query", {"from": "2026-12-31T00:00:00Z", "to": "2026-01-01T00:00:00Z"}, ADMIN)
    check("기록 검색: 시작이 끝보다 늦으면 400", s == 400, (s, q))

    # 7b. 사용자 관리(5-6) — 사용 이력·로그인 제한. 실제로 막히게 하지는 않는다(IP 실패 수가 남아 다른 시험의 로그인을 막을 수 있어서)
    vu = users[0]
    s, hist, _ = call("GET", f"/api/v1/users/{vu}/history?size=5", token=ADMIN)
    check("사용 이력: 최신순, 로그인·비밀번호 변경이 남음, size 지킴",
          s == 200 and 0 < len(hist) <= 5 and {"LOGIN", "PASSWORD_CHANGE"} <= {h["oper"] for h in hist}
          and [h["seqNo"] for h in hist] == sorted((h["seqNo"] for h in hist), reverse=True), (s, hist))
    s1, bl, _ = call("GET", "/api/v1/users/login-blocked", token=ADMIN)
    s2, rel, _ = call("POST", f"/api/v1/users/{vu}/login-unblock", token=ADMIN)
    s3, hist2, _ = call("GET", f"/api/v1/users/{vu}/history?size=1", token=ADMIN)
    check("로그인 제한: 목록 200(막히지 않은 사용자는 없음), 풀기 → released=false 이고 이력 LOGIN_UNBLOCK",
          s1 == 200 and isinstance(bl, list) and vu not in bl and s2 == 200 and rel == {"released": False}
          and s3 == 200 and hist2[0]["oper"] == "LOGIN_UNBLOCK", (s1, bl, s2, rel, s3, hist2))
    s1, _, _ = call("GET", f"/api/v1/users/{vu}/history", token=tok["ROLE_OPERATOR"])
    s2, _, _ = call("POST", f"/api/v1/users/{vu}/login-unblock", token=tok["ROLE_OPERATOR"])
    s3, _, _ = call("GET", f"/api/v1/users/no-such-{RUN}/history", token=ADMIN)
    check("사용 이력·제한 풀기: 운영자 403, 없는 사용자 404", s1 == 403 and s2 == 403 and s3 == 404, (s1, s2, s3))

    # 8. 홈 요약·시험 읽기·프로토콜
    s, b, _ = call("GET", "/api/v1/dashboard/summary", token=tok["ROLE_VIEWER"])
    check("홈 요약: 커넥터 건수·오늘 요청 건수·최근 오류", s == 200 and b["connectors"]["total"] >= 1 and b["connectors"]["published"] >= 1
          and b["today"]["requests"] >= 1 and isinstance(b["recentErrors"], list), (s, b if s != 200 else b["today"]))
    s, b, _ = call("POST", "/api/v1/plc-connector/test-read/byName/plc-e2e",
                   {"points": [{"name": "d100", "address": "D100", "type": "INT16"}, {"name": "d101", "address": "D101", "type": "INT16"}]}, ADMIN)
    check("시험 읽기: 값·plcStatus 0·걸린 시간", s == 200 and isinstance(b["values"].get("d100"), int) and b["plcStatus"]["code"] == 0
          and b["elapsedMs"] >= 0, (s, b))
    s, b, _ = call("POST", "/api/v1/plc-connector/test-read/byName/plc-e2e", {"points": [{"name": "x", "address": "Q1", "type": "INT16"}]}, ADMIN)
    s2, b2, _ = call("POST", f"/api/v1/plc-connector/test-read/byName/no-such-{RUN}", {"points": [{"name": "x", "address": "D1", "type": "INT16"}]}, ADMIN)
    s3, _, _ = call("POST", "/api/v1/plc-connector/test-read/byName/plc-e2e", {"points": [{"name": "x", "address": "D1", "type": "INT16"}]},
                    tok["ROLE_VIEWER"])
    check("시험 읽기: 틀린 주소 400, 없는 커넥터 404, 조회자 403", s == 400 and s2 == 404 and s3 == 403, (s, b, s2, b2, s3))
    s, b, _ = call("GET", "/api/v1/plc-connector/protocols", token=tok["ROLE_VIEWER"])
    check("프로토콜 설명: MELSEC 먼저, Modbus, 설정 항목·영역",
          s == 200 and [p["protocol"] for p in b] == ["MELSEC_MC", "MODBUS_TCP"] and "rawSupported" not in b[0]
          and any(x["key"] == "frame" and "4E_BINARY" in x["options"] for x in b[0]["settings"])
          and any(a["prefix"] == "IR" and a["readOnly"] for a in b[1]["areas"]), (s, b))

    # 9. 화면 서빙(선택)
    ui = os.environ.get("TC_E2E_UI_DIR")
    if ui:
        os.makedirs(os.path.join(ui, "assets"), exist_ok=True)
        marker = f"tc-ui-e2e-{RUN}"
        open(os.path.join(ui, "index.html"), "w", encoding="utf-8").write(f"<!doctype html><title>{marker}</title>")
        open(os.path.join(ui, "assets", f"app-{RUN}.js"), "w", encoding="utf-8").write("console.log(1)")
        s1, b1, h1 = call("GET", "/")
        s2, b2, _ = call("GET", "/plc/connectors")
        s3, b3, h3 = call("GET", f"/assets/app-{RUN}.js")
        s4, _, _ = call("GET", "/assets/none.js")
        s5, b5, _ = call("GET", f"/api/no-such-{RUN}", token=ADMIN)
        check("화면 서빙: / 와 SPA 경로는 index.html(no-cache), 자산은 1년 캐시, 없는 자산·API 는 404",
              s1 == 200 and marker in str(b1) and "no-cache" in (h1.get("Cache-Control") or "") and s2 == 200 and marker in str(b2)
              and s3 == 200 and "max-age=31536000" in (h3.get("Cache-Control") or "") and s4 == 404 and s5 == 404 and marker not in str(b5),
              (s1, h1.get("Cache-Control"), s2, s3, h3.get("Cache-Control"), s4, s5))
    else:
        print("SKIP 화면 서빙 — TC_E2E_UI_DIR 이 없습니다")
finally:
    for u in users:
        call("DELETE", f"/api/v1/users/{u}", token=ADMIN)

print(f"\n{sum(results)}/{len(results)} PASS")
sys.exit(0 if all(results) else 1)
