"""vplc 의 가상 Modbus 장치를 상대로 — 게이트웨이 한 포트 뒤 단위 ID 두 대, 레지스터 안 비트, 배율, 쓰기, 없는 단위 ID.

전제: vplc 가 떠 있다(TC_E2E_VPLC_URL). 가상 PLC mb-e2e·mb-e2e-2(Modbus 127.0.0.1:15020, 단위 ID 1·2)가 없으면 등록·실행한다(common.ensure_vplc).
vplc 가 없으면 건너뛴다.
가짜 장치(fake_modbus.py)로 하는 test_plc_modbus.py 와 달리 장치 값을 REST 로 바꿔 넣고 tc 로 읽는다.
"""
import json
import os
import sys
import time
import urllib.error
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402

RUN = str(int(time.time()))
PLC = "plc-vmodbus-e2e"
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


DEVS = [common.ensure_vplc(d) for d in common.E2E_MODBUS]
if None in DEVS:
    print(f"SKIP vplc 가 없습니다({common.VPLC_BASE}) — wow-tc-backend 에서 vplc 를 띄우세요")
    sys.exit(0)
MB1, MB2 = (common.VPLC_BASE + "/api/v1/vplc/modbus/" + d["id"] for d in DEVS)

TOKEN = common.admin_token()


def call(method, path, body=None):
    return http(method, common.BASE + path, body, TOKEN)


SEQ = [0]


def plc(opcode, data=None):
    SEQ[0] += 1
    return call("POST", "/api/v1/ifsvc/request-and-reply", {"transactionId": f"t-vmb-{RUN}-{SEQ[0]}", "messageId": f"m-{SEQ[0]}",
                                                           "opcode": opcode, "sender": "e2e", "receiver": "tc", "data": data or {}})


def register(name, if_type, points, settings=None, read_back=False):
    return call("POST", "/api/v1/ifsvc/management/register", {"name": name, "plcName": PLC, "interfaceType": if_type, "use": True,
                                                              "readBack": read_back, "points": points, "settings": settings or {}})


created = []
try:
    call("POST", f"/api/v1/plc-connector/control/release/byName/{PLC}")
    call("DELETE", f"/api/v1/plc-connector/delete/byName/{PLC}")
    s, base = call("GET", "/api/v1/plc-connector/find/byName/plc-e2e")
    conn = {k: v for k, v in base.items() if k not in ("id", "createdBy", "createdDate", "createdDateAsString", "updatedBy", "updatedDate",
                                                        "updatedDateAsString", "settings", "adapterClassName", "connection")}
    conn.update({"name": PLC, "protocol": "MODBUS_TCP", "hostIp": "127.0.0.1", "ports": [15020], "published": False, "status1": "REGISTERED",
                 "status2": "STOPPED", "description": "e2e 가상 Modbus", "settings": {"unitId": 1}})
    s, b = call("POST", "/api/v1/plc-connector/register", conn)
    check("커넥터 등록 200", s == 200, (s, b))
    s, b = call("POST", f"/api/v1/plc-connector/control/publish/byName/{PLC}")
    check("게시 200", s == 200 and b.get("success") is True, (s, b))

    # 장치 값 넣기 — 단위 1: HR0 = 0x000A(비트 1·3), HR1 = 253 / 단위 2: HR0 = 77
    http("PUT", MB1 + "/hr", {"start": 0, "values": [10, 253]})
    http("PUT", MB2 + "/hr", {"start": 0, "values": [77]})

    rd = f"e2e.vmb.read.{RUN}"
    s, b = register(rd, "READ", [{"name": "b1", "address": "HR1.1", "type": "BOOL"}, {"name": "b3", "address": "HR1.3", "type": "BOOL"},
                                 {"name": "b0", "address": "HR1.0", "type": "BOOL"},
                                 {"name": "temp", "address": "HR2", "type": "INT16", "scale": 0.1}])
    created.append(rd)
    s, r = plc(rd)
    check("단위 1: 레지스터 안 비트·배율(253×0.1)", s == 200 and r.get("data") == {"b1": True, "b3": True, "b0": False, "temp": 25.3}, (s, r))

    u2 = f"e2e.vmb.unit2.{RUN}"
    register(u2, "READ", [{"name": "v", "address": "HR1", "type": "UINT16"}], settings={"unitId": 2})
    created.append(u2)
    s, r = plc(u2)
    check("단위 2(인터페이스 settings unitId): 같은 포트의 다른 장치 값 77", s == 200 and r.get("data") == {"v": 77}, (s, r))

    u9 = f"e2e.vmb.unit9.{RUN}"
    register(u9, "READ", [{"name": "v", "address": "HR1", "type": "UINT16"}], settings={"unitId": 9})
    created.append(u9)
    s, r = plc(u9)
    check("없는 단위 ID → plcStatus 예외 0x0B(게이트웨이 대상 응답 없음)", r.get("success") is False and r.get("plcStatus", {}).get("code") == 11, r)

    wr = f"e2e.vmb.write.{RUN}"
    register(wr, "WRITE", [{"name": "set", "address": "HR10", "type": "INT16", "scale": 0.1, "min": 0, "max": 100},
                           {"name": "lamp", "address": "CO5", "type": "BOOL"}], read_back=True)
    created.append(wr)
    s, w = plc(wr, {"set": 36.6, "lamp": True})
    check("쓰기 36.6·코일 → readBack", s == 200 and w.get("data") == {"set": 36.6, "lamp": True}, (s, w))
    s, hr = http("GET", MB1 + "/hr?start=9&count=1")
    s2, co = http("GET", MB1 + "/co?start=4&count=1")
    check("가상 장치에는 원시 366·코일 켜짐(프로토콜 주소 9·4)", hr.get("values") == [366] and co.get("values") == [True], (hr, co))
finally:
    for name in created:
        call("DELETE", f"/api/v1/ifsvc/management/delete/byName/{name}")
    call("POST", f"/api/v1/plc-connector/control/release/byName/{PLC}")
    s, _ = call("DELETE", f"/api/v1/plc-connector/delete/byName/{PLC}")
    check("정리: 인터페이스·커넥터 삭제", s == 200, s)

print(f"\n{sum(results)}/{len(results)} PASS")
sys.exit(0 if all(results) else 1)
