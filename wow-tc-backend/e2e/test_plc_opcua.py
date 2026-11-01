"""OPC UA 커넥터(인터페이스 세부 정의 D단계, docs/opcua-design.md) — 가상 OPC UA 서버(vplc-opcua)를 상대로.

- 커넥터 등록(어댑터 클래스·설정 기본값)·연결 시험·게시(연결 상태)
- 노드 찾아보기(Objects → Line1 → 변수·점 형 추정), OPC UA 가 아닌 커넥터 400
- READ·WRITE(readBack)·배율, 없는 노드 → plcStatus Bad_NodeIdUnknown, 형 범위 밖 400, 요청 미리보기(서비스 단위)
- SUBSCRIBE 인터페이스 — 흉내 값(Line1.Counter, 1초마다)이 바뀌면 감사 RESPONSE(sender opcua-subscription)가 쌓인다. WRITE 에 SUBSCRIBE 는 400
- D2(§7): Basic256Sha256 + 사용자 이름 커넥터, 비밀번호는 조회에 안 나옴(passwordSet), nsu= 점, 틀린 비밀번호 연결 시험 실패,
  클라이언트 인증서 내려받기, 믿은 서버 인증서 지문·잊기
전제: vplc-opcua 가 떠 있다(TC_E2E_VPLC_OPCUA_URL, 기본 http://localhost:8092 — OPC UA opc.tcp://127.0.0.1:4840/vplc). 없으면 건너뛴다.
"""
import json
import os
import sys
import time
import urllib.error
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402

SIM = os.environ.get("TC_E2E_VPLC_OPCUA_URL", "http://localhost:8092")
RUN = str(int(time.time()))
PLC = "plc-opcua-e2e"
results = []


def check(name, cond, detail=""):
    results.append(bool(cond))
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else f"  -> {str(detail)[:300]}"), flush=True)


def http(method, url, body=None, token=None):
    req = urllib.request.Request(url, data=json.dumps(body).encode() if body is not None else None, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", token)
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


try:
    s, info = http("GET", SIM + "/api/v1/opcua")
except urllib.error.URLError:
    s, info = 0, None
if s != 200:
    print(f"SKIP 가상 OPC UA 서버가 없습니다({SIM}) — wow-tc-backend 에서 gradlew :vplc-opcua:bootRun")
    sys.exit(0)
NS = info["namespaceIndex"]


def node(path):
    return f"ns={NS};s={path}"


def expanded(path):
    return f"nsu={info['namespaceUri']};s={path}"


SIM_PASSWORD = os.environ.get("VPLC_OPCUA_PASSWORD", "tc-e2e-pass")
SEC = "plc-opcua-sec-e2e"
SEC_BAD = "plc-opcua-bad-e2e"


TOKEN = common.admin_token()


def call(method, path, body=None):
    return http(method, common.BASE + path, body, TOKEN)


SEQ = [0]


def plc(opcode, data=None):
    SEQ[0] += 1
    return call("POST", "/api/v1/ifsvc/request-and-reply", {"transactionId": f"t-ua-{RUN}-{SEQ[0]}", "messageId": f"m-{SEQ[0]}",
                                                           "opcode": opcode, "sender": "e2e", "receiver": "tc", "data": data or {}})


created = []
try:
    # 1. 커넥터 — 같은 이름이 남아 있으면 지우고 새로
    call("POST", f"/api/v1/plc-connector/control/release/byName/{PLC}")
    call("DELETE", f"/api/v1/plc-connector/delete/byName/{PLC}")
    s, base = call("GET", "/api/v1/plc-connector/find/byName/plc-e2e")
    conn = {k: v for k, v in base.items() if k not in ("id", "createdBy", "createdDate", "createdDateAsString", "updatedBy", "updatedDate",
                                                        "updatedDateAsString", "settings", "adapterClassName", "connection")}
    conn.update({"name": PLC, "protocol": "OPC_UA", "hostIp": "127.0.0.1", "ports": [4840], "published": False, "status1": "REGISTERED",
                 "status2": "STOPPED", "description": "e2e 가상 OPC UA", "settings": {"endpointPath": "/vplc", "publishingIntervalMs": 200,
                                                                                 "samplingIntervalMs": 100}})
    s, b = call("POST", "/api/v1/plc-connector/register", conn)
    check("커넥터 등록 200", s == 200, (s, b))
    s, c = call("GET", f"/api/v1/plc-connector/find/byName/{PLC}")
    check("어댑터 클래스 OpcUaAdapter, 설정 기본값", c.get("adapterClassName", "").endswith("OpcUaAdapter")
          and c.get("settings", {}).get("requestTimeoutMs") == 5000, c)
    s, t = call("POST", f"/api/v1/plc-connector/test-connection/byName/{PLC}")
    check("연결 시험 OK(서버 상태 읽기)", t.get("result") == "CONNECTION_TEST_OK" and t.get("endpoint") == "opc.tcp://127.0.0.1:4840/vplc", t)
    s, b = call("POST", f"/api/v1/plc-connector/control/publish/byName/{PLC}")
    check("게시 200", s == 200 and b.get("success") is True, (s, b))
    time.sleep(1)
    s, c = call("GET", f"/api/v1/plc-connector/find/byName/{PLC}")
    conn_state = c.get("connection") or {}
    check("연결 상태: 채널 1", conn_state.get("activeChannels") == 1, conn_state)

    # 2. 찾아보기
    s, top = call("POST", f"/api/v1/plc-connector/browse/byName/{PLC}", {})
    line1 = next((n for n in top if n.get("browseName") == "Line1"), None) if isinstance(top, list) else None
    check("찾아보기 Objects → Line1(자식 있음)", line1 is not None and line1.get("hasChildren") is True, top)
    s, vars_ = call("POST", f"/api/v1/plc-connector/browse/byName/{PLC}", {"nodeId": line1["nodeId"] if line1 else None})
    by = {n["browseName"]: n for n in vars_} if isinstance(vars_, list) else {}
    check("찾아보기 Line1 → 변수와 점 형(Temp FLOAT64, Speed UINT16, Running BOOL), 주소는 nsu=", by.get("Temp", {}).get("type") == "FLOAT64"
          and by.get("Speed", {}).get("type") == "UINT16" and by.get("Running", {}).get("type") == "BOOL"
          and by.get("Temp", {}).get("nodeId") == expanded("Line1.Temp"), vars_)
    s, b = call("POST", "/api/v1/plc-connector/browse/byName/plc-e2e", {})
    check("MELSEC 커넥터 찾아보기 400", s == 400, (s, b))

    # 3. 읽기·쓰기
    rd = f"e2e.ua.read.{RUN}"
    s, b = call("POST", "/api/v1/ifsvc/management/register", {
        "name": rd, "plcName": PLC, "interfaceType": "READ", "use": True, "description": "e2e OPC UA read",
        "points": [{"name": "temp", "address": node("Line1.Temp"), "type": "FLOAT64"},
                   {"name": "speed", "address": node("Line1.Speed"), "type": "UINT16"},
                   {"name": "running", "address": node("Line1.Running"), "type": "BOOL"},
                   {"name": "recipe", "address": node("Line1.Recipe"), "type": "STRING"},
                   {"name": "raw", "address": node("Line1.Raw"), "type": "INT16", "scale": 0.1, "unit": "℃"}]})
    check("READ 등록 200", s == 200, (s, b))
    created.append(rd)
    http("PUT", SIM + "/api/v1/opcua/tags/Line1.Temp", {"value": 25.5})
    http("PUT", SIM + "/api/v1/opcua/tags/Line1.Raw", {"value": 253})
    s, r = plc(rd)
    d = r.get("data", {}) if isinstance(r, dict) else {}
    check("읽기 200 — 값·형·배율(253×0.1)", s == 200 and d.get("temp") == 25.5 and isinstance(d.get("speed"), int) and d.get("running") in (True, False)
          and isinstance(d.get("recipe"), str) and d.get("raw") == 25.3, (s, r))
    check("plcStatus OPC_UA code 0", r.get("plcStatus") == {"protocol": "OPC_UA", "code": 0, "text": "OK"}, r.get("plcStatus"))

    wr = f"e2e.ua.write.{RUN}"
    s, b = call("POST", "/api/v1/ifsvc/management/register", {
        "name": wr, "plcName": PLC, "interfaceType": "WRITE", "use": True, "readBack": True,
        "points": [{"name": "count", "address": node("Line1.Count"), "type": "INT32"},
                   {"name": "recipe", "address": node("Line1.Recipe"), "type": "STRING"}]})
    created.append(wr)
    s, w = plc(wr, {"count": 1234, "recipe": f"R-{RUN[-4:]}"})
    check("쓰기 200, readBack 값", s == 200 and w.get("data") == {"count": 1234, "recipe": f"R-{RUN[-4:]}"}, (s, w))
    s, tag = http("GET", SIM + "/api/v1/opcua/tags/Line1.Count")
    check("가상 서버 태그 값이 바뀜(1234)", tag.get("value") == 1234, tag)

    bad = f"e2e.ua.bad.{RUN}"
    call("POST", "/api/v1/ifsvc/management/register", {"name": bad, "plcName": PLC, "interfaceType": "READ", "use": True,
                                                       "points": [{"name": "temp", "address": node("Line1.Temp"), "type": "FLOAT64"},
                                                                  {"name": "nope", "address": node("Line1.Nope"), "type": "INT32"}]})
    created.append(bad)
    s, r = plc(bad)
    check("없는 노드 → 업무 실패, plcStatus Bad_NodeIdUnknown, 좋은 값은 실림", r.get("success") is False
          and "Bad_NodeIdUnknown" in str(r.get("plcStatus", {}).get("text")) and r.get("data", {}).get("temp") is not None, r)

    ws = f"e2e.ua.speed.{RUN}"
    call("POST", "/api/v1/ifsvc/management/register", {"name": ws, "plcName": PLC, "interfaceType": "WRITE", "use": True,
                                                       "points": [{"name": "speed", "address": node("Line1.Speed"), "type": "UINT16"}]})
    created.append(ws)
    s, r = plc(ws, {"speed": 70000})
    check("UINT16 범위 밖 쓰기 400", s == 400 and "범위" in str(r), (s, r))

    s, p = call("POST", "/api/v1/ifsvc/management/plan", {"interfaceInfo": {"name": "x", "plcName": PLC, "interfaceType": "READ",
                                                                            "points": [{"name": "a", "address": node("Line1.Temp"), "type": "FLOAT64"},
                                                                                       {"name": "b", "address": node("Line1.Speed"), "type": "UINT16"}]}})
    reqs = p.get("requests", []) if isinstance(p, dict) else []
    check("미리보기 Read(Value) 2 노드", s == 200 and len(reqs) == 1 and reqs[0].get("function") == "Read(Value)" and reqs[0].get("count") == 2, p)
    s, b = call("POST", "/api/v1/ifsvc/management/register", {"name": f"e2e.ua.badaddr.{RUN}", "plcName": PLC, "interfaceType": "READ",
                                                              "points": [{"name": "a", "address": "D100", "type": "INT16"}]})
    check("NodeId 가 아닌 주소 400", s == 400 and "NodeId" in str(b), (s, b))

    # 4. 구독 — Line1.Counter 는 1초마다 1씩. 알림마다 감사 RESPONSE(sender opcua-subscription)
    sub = f"e2e.ua.sub.{RUN}"
    s, b = call("POST", "/api/v1/ifsvc/management/register", {"name": sub, "plcName": PLC, "interfaceType": "READ", "use": True,
                                                              "settings": {"accessMode": "SUBSCRIBE"},
                                                              "points": [{"name": "counter", "address": node("Line1.Counter"), "type": "INT32"}]})
    check("SUBSCRIBE 인터페이스 등록 200", s == 200, (s, b))
    created.append(sub)
    s, b = call("POST", "/api/v1/ifsvc/management/register", {"name": f"e2e.ua.subw.{RUN}", "plcName": PLC, "interfaceType": "WRITE",
                                                              "settings": {"accessMode": "SUBSCRIBE"},
                                                              "points": [{"name": "c", "address": node("Line1.Count"), "type": "INT32"}]})
    check("WRITE 에 SUBSCRIBE 400", s == 400 and "READ" in str(b), (s, b))
    # 맞추기는 10초마다 — 구독이 생기고 알림이 두 번 이상 올 때까지 기다린다
    rows = []
    for _ in range(30):
        time.sleep(1)
        s, q = call("POST", "/api/v1/ifsvc/audit/search/query", {"opcode": sub, "messageTypes": ["RESPONSE"], "size": 20})
        rows = [x for x in (q.get("items", []) if isinstance(q, dict) else []) if x.get("sender") == "opcua-subscription"]
        if len(rows) >= 2:
            break
    values = sorted({json.dumps(x.get("dataMap") or x.get("data")) for x in rows})
    check("구독 알림 감사 2건 이상, 값이 서로 다름(바뀔 때마다)", len(rows) >= 2 and len(values) >= 2, (len(rows), values[:3]))
    # 5. D2 — 보안 정책·로그인·인증서(docs/opcua-design.md §7)
    for name in (SEC, SEC_BAD):
        call("POST", f"/api/v1/plc-connector/control/release/byName/{name}")
        call("DELETE", f"/api/v1/plc-connector/delete/byName/{name}")
    sec = dict(conn, name=SEC, description="e2e OPC UA 보안", password=SIM_PASSWORD,
               settings={"endpointPath": "/vplc", "securityPolicy": "BASIC256SHA256", "authMode": "USERNAME", "username": "tc-e2e"})
    s, b = call("POST", "/api/v1/plc-connector/register", sec)
    check("D2: 보안 커넥터 등록 200(Basic256Sha256 + 사용자 이름)", s == 200, (s, b))
    s, c = call("GET", f"/api/v1/plc-connector/find/byName/{SEC}")
    check("D2: 조회에 비밀번호 없음, passwordSet true, 모드 SIGN_AND_ENCRYPT", isinstance(c, dict) and "password" not in c and "passwordEnc" not in c
          and c.get("passwordSet") is True and c.get("settings", {}).get("securityMode") == "SIGN_AND_ENCRYPT", c)
    s, t = call("POST", f"/api/v1/plc-connector/test-connection/byName/{SEC}")
    check("D2: 연결 시험 OK(암호화·로그인)", t.get("result") == "CONNECTION_TEST_OK", t)
    s, fp = call("GET", f"/api/v1/plc-connector/opcua/server-certificate/byName/{SEC}")
    check("D2: 서버 인증서를 처음 믿음(지문)", isinstance(fp, dict) and fp.get("thumbprint"), fp)
    s, b = call("POST", f"/api/v1/plc-connector/control/publish/byName/{SEC}")
    check("D2: 게시 200", s == 200 and b.get("success") is True, (s, b))
    rs = f"e2e.ua.sec.{RUN}"
    call("POST", "/api/v1/ifsvc/management/register", {"name": rs, "plcName": SEC, "interfaceType": "READ", "use": True,
                                                       "points": [{"name": "temp", "address": expanded("Line1.Temp"), "type": "FLOAT64"}]})
    created.append(rs)
    http("PUT", SIM + "/api/v1/opcua/tags/Line1.Temp", {"value": 31.5})
    s, r = plc(rs)
    check("D2: 보안 세션으로 nsu= 점 읽기(31.5)", s == 200 and r.get("data") == {"temp": 31.5}, (s, r))
    bad = dict(sec, name=SEC_BAD, password="wrong-password")
    call("POST", "/api/v1/plc-connector/register", bad)
    s, t = call("POST", f"/api/v1/plc-connector/test-connection/byName/{SEC_BAD}")
    check("D2: 틀린 비밀번호 연결 시험 실패", t.get("result") == "CONNECTION_TEST_ERROR", t)
    req = urllib.request.Request(common.BASE + "/api/v1/plc-connector/opcua/client-certificate")
    req.add_header("Authorization", TOKEN)
    with urllib.request.urlopen(req, timeout=20) as resp:
        der = resp.read()
    check("D2: tc 클라이언트 인증서 내려받기(DER)", len(der) > 300 and der[0] == 0x30, len(der))
    s, f = call("POST", f"/api/v1/plc-connector/opcua/forget-server-certificate/byName/{SEC_BAD}")
    check("D2: 서버 인증서 잊기 200", s == 200 and "forgotten" in f, (s, f))
finally:
    for name in created:
        call("DELETE", f"/api/v1/ifsvc/management/delete/byName/{name}")
    for name in (SEC, SEC_BAD):
        call("POST", f"/api/v1/plc-connector/control/release/byName/{name}")
        call("DELETE", f"/api/v1/plc-connector/delete/byName/{name}")
    call("POST", f"/api/v1/plc-connector/control/release/byName/{PLC}")
    s, _ = call("DELETE", f"/api/v1/plc-connector/delete/byName/{PLC}")
    check("정리: 인터페이스·커넥터 삭제", s == 200, s)

print(f"\n{sum(results)}/{len(results)} PASS")
sys.exit(0 if all(results) else 1)
