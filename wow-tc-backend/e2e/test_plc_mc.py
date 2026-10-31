"""MELSEC 보강(PLC 일반화 2단계, #12) — docs/plc-protocol-design.md §6.1·§13.

- vplc 가 실제 PLC 처럼 오류 종료 코드로 답한다(없는 메모리) — tc 는 시간 초과가 아니라 502 + plcStatus, 연결은 그대로.
- 연결 시험: 포트마다 TCP + 루프백(0619).
- 4E 프레임: 커넥터를 4E 로 바꿔 읽고, 시간 초과에도 연결을 끊지 않는다(일련번호로 늦은 응답을 버림).
- 주기적 루프백: 답하지 않는 PLC(vplc 멈춤)를 찾아 연결을 닫고, 다시 답하면 다시 잇는다.
전제: vplc vplc-e2e(15000, 포트당 연결 1) 와 plc-e2e 게시됨, tc 는 PLC_REQUEST_TIMEOUT_MS=3000. 끝나면 plc-e2e 설정을 되돌린다."""
import json
import os
import sys
import time
import urllib.error
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402

TOKEN = common.admin_token()
RUN = str(int(time.time()))
PLC = "plc-e2e"
VPLC = "vplc-e2e"


def call(method, path, body=None, base=None, token=True):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request((base or common.BASE) + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
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


def vplc(op):
    s = call("POST", f"/api/v1/vplc/control/{op}/byName/{VPLC}", base=common.VPLC_BASE, token=False)[0]
    if s != 200:
        raise SystemExit(f"vplc {op} 실패: HTTP {s}")
    return s


results = []


def check(name, cond, detail=""):
    results.append(bool(cond))
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else f"  -> {str(detail)[:300]}"), flush=True)


SEQ = [0]


def plc(opcode):
    SEQ[0] += 1
    t0 = time.time()
    s, b = call("POST", "/api/v1/ifsvc/request-and-reply", {"transactionId": f"t-mc-{RUN}-{SEQ[0]}", "messageId": f"m-{SEQ[0]}",
                                                           "opcode": opcode, "sender": "e2e", "receiver": "tc", "data": {}})
    return s, b, time.time() - t0


def connector():
    return call("GET", f"/api/v1/plc-connector/find/byName/{PLC}")[1]


def set_settings(settings):
    info = connector()
    info = {k: v for k, v in info.items() if k != "connection"}
    info["settings"] = settings
    s, b = call("POST", "/api/v1/plc-connector/update", info)
    call("POST", f"/api/v1/plc-connector/control/release/byName/{PLC}")
    call("POST", f"/api/v1/plc-connector/control/publish/byName/{PLC}")
    return s


def wait(fn, seconds):
    deadline = time.time() + seconds
    while time.time() < deadline:
        v = fn()
        if v:
            return v
        time.sleep(0.5)
    return fn()


created = []
PTS = f"e2e.mc.pts.{RUN}"
MISSING = f"e2e.mc.missing.{RUN}"
try:
    for name, address in ((PTS, "D100"), (MISSING, "W0")):
        s, b = call("POST", "/api/v1/ifsvc/management/register", {"name": name, "plcName": PLC, "interfaceType": "READ",
                                                                  "use": True, "sync": True, "reply": True,
                                                                  "points": [{"name": "v", "address": address, "type": "INT16"}]})
        created.append(name)

    # 1. 없는 메모리 — 시간 초과가 아니라 PLC 오류 종료 코드(0xC056)
    s, b, dt = plc(MISSING)
    check("vplc 에 없는 메모리(W) → success=false ERR_502_TC_PLC_ERROR, 종료 코드 0xC056, 바로(시간 초과 아님)",
          s == 200 and b.get("success") is False and b.get("errorCode") == "ERR_502_TC_PLC_ERROR" and (b.get("plcStatus") or {}).get("code") == 0xC056 and dt < 2,
          (s, dt, b))
    s, b, _ = plc("plc.e2e.d100.read")
    check("오류 응답 뒤에도 같은 연결로 plc.e2e.d100.read 읽기 200", s == 200 and b.get("success") is True, (s, b))

    # 2. 연결 시험 — 포트마다 TCP + 루프백
    s, b = call("POST", f"/api/v1/plc-connector/test-connection/byName/{PLC}")
    check("연결 시험 결과에 포트별 항목(port:15000)", s == 200 and "port:15000" in b and "result" in b, (s, b))
    call("POST", f"/api/v1/plc-connector/control/release/byName/{PLC}")
    s, b = call("POST", f"/api/v1/plc-connector/test-connection/byName/{PLC}")
    check("해제한 뒤(슬롯 빔) 연결 시험 → OK(TCP + 루프백)", s == 200 and b.get("port:15000") == "OK" and b.get("result") == "CONNECTION_TEST_OK", (s, b))
    call("POST", f"/api/v1/plc-connector/control/publish/byName/{PLC}")

    # 3. 4E — 커넥터 프레임을 바꾸면 모든 인터페이스가 4E 로 읽는다
    s = set_settings({"frame": "4E_BINARY", "healthCheckIntervalSec": 0})
    got = connector()
    check("커넥터 설정을 4E 로(정규화)", s == 200 and got.get("settings", {}).get("frame") == "4E_BINARY", got.get("settings"))
    s, b, _ = plc(PTS)
    s2, b2, _ = plc("plc.e2e.d100.read")
    check("4E 로 두 인터페이스(D100) 읽기가 같은 값", s == 200 and s2 == 200 and b["data"]["v"] == b2["data"]["d100"], (s, b, s2, b2))

    # 4. 4E 시간 초과 — 연결을 끊지 않는다
    connected_at = (connector().get("connection") or {}).get("lastConnectedAt")
    vplc("pause")
    s, b, dt = plc(PTS)
    check("vplc 멈춤 → 4E 읽기 504(약 3초)", s == 504 and 2.5 < dt < 5, (s, dt, b))
    conn = connector().get("connection") or {}
    check("4E 시간 초과 뒤에도 연결은 그대로(채널 1, 다시 잇지 않음)", conn.get("activeChannels") == 1
          and conn.get("lastConnectedAt") == connected_at and conn.get("lastEvent") != "DISCONNECT", conn)
    vplc("resume")
    s, b, _ = plc(PTS)
    check("vplc 재개 → 같은 연결로 200", s == 200 and b.get("success") is True, (s, b))

    # 5. 주기적 루프백 — 답하지 않는 PLC 의 연결을 닫고, 다시 답하면 다시 잇는다
    set_settings({"frame": "3E_BINARY", "healthCheckIntervalSec": 2})
    wait(lambda: (connector().get("connection") or {}).get("activeChannels") == 1, 10)
    vplc("pause")
    err = wait(lambda: "루프백" in str((connector().get("connection") or {}).get("lastError")), 12)
    check("vplc 멈춤 → 루프백 실패로 연결을 닫음(마지막 오류에 남음)", err, connector().get("connection"))
    vplc("resume")
    ok = wait(lambda: plc(PTS)[0] == 200, 20)
    check("vplc 재개 → 다시 연결해 요청 200", ok)
finally:
    vplc("resume")
    set_settings({})
    for name in created:
        call("DELETE", f"/api/v1/ifsvc/management/delete/byName/{name}")
    s, b, _ = plc("plc.e2e.d100.read")
    check("정리: 기본 설정(3E, 루프백 30초)으로 되돌리고 plc.e2e.d100.read 읽기 200",
          s == 200 and connector().get("settings", {}).get("frame") == "3E_BINARY", (s, b, connector().get("settings")))

print(f"\n{sum(results)}/{len(results)} PASS")
sys.exit(0 if all(results) else 1)
