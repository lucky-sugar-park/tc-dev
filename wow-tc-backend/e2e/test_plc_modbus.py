"""Modbus TCP (PLC 일반화 3단계) — docs/plc-protocol-design.md §6.2·§14.

가짜 Modbus 장치(fake_modbus.py, :15200)를 이 시험이 띄우고 내린다. 커넥터 plc-modbus-e2e 를 만들어 시험한 뒤 지운다.
- 커넥터: protocol MODBUS_TCP → 어댑터 클래스·설정 기본값 자동, 연결 시험(TCP + 레지스터 읽기), 점 없는 인터페이스 거절.
- 점 읽기(HR·IR·DI, 정수·32비트·문자열·비트), 쓰기(HR·CO, readBack), 워드 순서 LITTLE(CDAB).
- 예외 응답 → success=false + plcStatus(MODBUS_TCP, 예외 코드), 연결은 그대로.
- 시간 초과에도 연결을 끊지 않는다(트랜잭션 ID 로 늦은 응답을 버림). 쉬는 연결 확인으로 답하지 않는 장치의 연결을 닫고 다시 잇는다.
전제: tc 는 PLC_REQUEST_TIMEOUT_MS=3000."""
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
TOKEN = common.admin_token()
RUN = str(int(time.time()))
PLC = "plc-modbus-e2e"
PORT = 15200


def call(method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(common.BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
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


results = []


def check(name, cond, detail=""):
    results.append(bool(cond))
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else f"  -> {str(detail)[:300]}"), flush=True)


SEQ = [0]


def plc(opcode, data=None):
    SEQ[0] += 1
    t0 = time.time()
    s, b = call("POST", "/api/v1/ifsvc/request-and-reply", {"transactionId": f"t-mb-{RUN}-{SEQ[0]}", "messageId": f"m-{SEQ[0]}",
                                                           "opcode": opcode, "sender": "e2e", "receiver": "tc", "data": data or {}})
    return s, b, time.time() - t0


def connector():
    return call("GET", f"/api/v1/plc-connector/find/byName/{PLC}")[1]


def conn():
    c = connector()
    return (c.get("connection") or {}) if isinstance(c, dict) else {}


def set_settings(settings):
    info = {k: v for k, v in connector().items() if k != "connection"}
    info["settings"] = settings
    s, _ = call("POST", "/api/v1/plc-connector/update", info)
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


def register(name, if_type, points, read_back=False):
    body = {"name": name, "plcName": PLC, "interfaceType": if_type, "use": True,
            "readBack": read_back, "description": "e2e modbus"}
    if points is not None:
        body["points"] = points
    s, b = call("POST", "/api/v1/ifsvc/management/register", body)
    if s == 200:
        created.append(name)
    return s, b


def drop_connector():
    call("POST", f"/api/v1/plc-connector/control/release/byName/{PLC}")
    call("DELETE", f"/api/v1/plc-connector/delete/byName/{PLC}")


log = open(os.path.join(HERE, "fake_modbus.out.log"), "w", encoding="utf-8")
fake = subprocess.Popen([sys.executable, "-u", os.path.join(HERE, "fake_modbus.py"), str(PORT)], stdout=log, stderr=subprocess.STDOUT)
time.sleep(1)
drop_connector()
try:
    # 1. 커넥터
    s, b = call("POST", "/api/v1/plc-connector/register", {
        "name": PLC, "protocol": "MODBUS_TCP", "hostIp": "127.0.0.1", "ports": [PORT], "connectionVips": ["127.0.0.1"],
        "connLimitPerPort": 1, "manufacturer": "e2e", "settings": {"healthCheckIntervalSec": 0}, "description": "tc e2e — 가짜 Modbus 장치"})
    got = connector()
    check("Modbus 커넥터 등록 → 어댑터 클래스·설정 기본값 자동", s == 200 and got.get("adapterClassName", "").endswith("ModbusTcpAdapter")
          and got.get("settings") == {"unitId": 1, "byteOrder": "BIG", "wordOrder": "BIG", "addressBase": 1, "maxRegistersPerRequest": 125,
                                      "maxCoilsPerRequest": 2000, "healthCheckIntervalSec": 0}, (s, b, got))
    s, b = call("POST", "/api/v1/plc-connector/register", {
        "name": f"{PLC}-bad", "protocol": "MODBUS_TCP", "hostIp": "127.0.0.1", "ports": [PORT], "connectionVips": ["127.0.0.1"],
        "settings": {"frame": "3E_BINARY"}})
    check("Modbus 설정에 없는 항목(frame) → 400", s == 400, (s, b))
    s, b = call("POST", f"/api/v1/plc-connector/control/publish/byName/{PLC}")
    check("게시 200, 채널 1", s == 200 and wait(lambda: conn().get("activeChannels") == 1, 5), (s, b, conn()))
    s, b = call("POST", f"/api/v1/plc-connector/test-connection/byName/{PLC}")
    check("연결 시험(TCP + 레지스터 읽기) OK", s == 200 and b.get(f"port:{PORT}") == "OK" and b.get("result") == "CONNECTION_TEST_OK", (s, b))

    # 2. 등록 검증
    s, b = register(f"e2e.mb.nopts.{RUN}", "READ", None)
    check("점 없는 인터페이스 → 400", s == 400, (s, b))
    s, b = register(f"e2e.mb.bad1.{RUN}", "READ", [{"name": "c", "address": "CO1", "type": "INT16"}])
    check("코일에 INT16 → 400", s == 400, (s, b))
    s, b = register(f"e2e.mb.bad2.{RUN}", "WRITE", [{"name": "i", "address": "IR1", "type": "INT16"}])
    check("입력 레지스터를 쓰기 인터페이스에 → 400", s == 400, (s, b))

    # 3. 읽기
    READ = f"e2e.mb.read.{RUN}"
    s, b = register(READ, "READ", [
        {"name": "a", "address": "HR1", "type": "INT16"},
        {"name": "b", "address": "40002", "type": "INT32"},
        {"name": "s", "address": "IR1", "type": "STRING", "length": 3},
        {"name": "d", "address": "DI1", "type": "BOOL", "count": 3}])
    check("READ 등록 200", s == 200, (s, b))
    s, b, _ = plc(READ)
    check("읽기: HR INT16·INT32(BIG), IR 문자열, DI 비트", s == 200 and b.get("success") is True
          and b.get("data") == {"a": 100, "b": 65538, "s": "ABC", "d": [True, False, True]}, (s, b))
    check("plcStatus: MODBUS_TCP, code 0", b.get("plcStatus") == {"protocol": "MODBUS_TCP", "code": 0, "text": "OK"}, b.get("plcStatus"))

    # 4. 쓰기(readBack)
    WRITE = f"e2e.mb.write.{RUN}"
    points = [{"name": "f", "address": "HR11", "type": "FLOAT32"},
              {"name": "n", "address": "HR13", "type": "INT16"},
              {"name": "c", "address": "CO1", "type": "BOOL", "count": 2},
              {"name": "one", "address": "CO5", "type": "BOOL"}]
    s, b = register(WRITE, "WRITE", points, read_back=True)
    check("WRITE 등록 200(readBack)", s == 200, (s, b))
    values = {"f": 1.5, "n": -5, "c": [True, False], "one": True}
    s, b, _ = plc(WRITE, values)
    check("쓰기 200, readBack 값이 쓴 값과 같다", s == 200 and b.get("success") is True and b.get("data") == values, (s, b))
    s, b, _ = plc(WRITE, {"f": 2.25, "n": 70000, "c": [True, False], "one": False})
    check("범위 밖 값(INT16 70000) → 400", s == 400 or (isinstance(b, dict) and b.get("errorCode") == "ERR_400"), (s, b))

    # 5. 워드 순서 LITTLE(CDAB)
    s = set_settings({"wordOrder": "LITTLE", "healthCheckIntervalSec": 0})
    s2, b, _ = wait(lambda: (lambda r: r if r[0] == 200 else None)(plc(READ)), 10) or (0, {}, 0)
    check("wordOrder LITTLE → 같은 레지스터 0x0001,0x0002 가 INT32 131073", s == 200 and s2 == 200 and b["data"]["b"] == 131073, (s, s2, b))
    set_settings({"healthCheckIntervalSec": 0})

    # 6. 예외 응답
    EXC = f"e2e.mb.exc.{RUN}"
    register(EXC, "READ", [{"name": "x", "address": "HR2000", "type": "INT16"}])
    wait(lambda: conn().get("activeChannels") == 1, 5)
    s, b, dt = plc(EXC)
    check("장치에 없는 주소 → success=false ERR_502_TC_PLC_ERROR, 예외 코드 2, 바로", s == 200 and b.get("success") is False
          and b.get("errorCode") == "ERR_502_TC_PLC_ERROR" and (b.get("plcStatus") or {}).get("code") == 2
          and (b.get("plcStatus") or {}).get("protocol") == "MODBUS_TCP" and dt < 2, (s, dt, b))
    s, b, _ = plc(READ)
    check("예외 응답 뒤 같은 연결로 읽기 200", s == 200 and b.get("success") is True, (s, b))

    # 7. 시간 초과 — 연결을 끊지 않는다
    HANG = f"e2e.mb.hang.{RUN}"
    register(HANG, "READ", [{"name": "h", "address": "HR501", "type": "INT16"}])
    connected_at = conn().get("lastConnectedAt")
    s, b, dt = plc(HANG)
    check("답하지 않는 주소 → 504(약 3초)", s == 504 and 2.5 < dt < 5, (s, dt, b))
    c = conn()
    check("시간 초과 뒤에도 연결은 그대로(채널 1, 다시 잇지 않음)", c.get("activeChannels") == 1 and c.get("lastConnectedAt") == connected_at
          and c.get("lastEvent") != "DISCONNECT", c)
    s, b, _ = plc(READ)
    check("같은 연결로 다음 읽기 200(늦은 응답은 없음)", s == 200 and b.get("data", {}).get("a") == 100, (s, b))

    # 8. 쉬는 연결 확인 — 답하지 않는 장치의 연결을 닫고, 다시 답하면 다시 잇는다
    MUTE = f"e2e.mb.mute.{RUN}"
    register(MUTE, "WRITE", [{"name": "sec", "address": "HR991", "type": "UINT16"}])
    set_settings({"healthCheckIntervalSec": 2})
    wait(lambda: conn().get("activeChannels") == 1, 10)
    s, b, _ = plc(MUTE, {"sec": 8})
    check("장치를 8초 동안 조용하게(쓰기 200)", s == 200 and b.get("success") is True, (s, b))
    err = wait(lambda: "연결 확인" in str(conn().get("lastError")), 10)
    check("답하지 않음 → 연결 확인 실패로 연결을 닫음(마지막 오류에 남음)", err, conn())
    ok = wait(lambda: plc(READ)[0] == 200, 20)
    check("장치가 다시 답함 → 다시 연결해 읽기 200", ok)
finally:
    for name in created:
        call("DELETE", f"/api/v1/ifsvc/management/delete/byName/{name}")
    drop_connector()
    s, b = call("GET", f"/api/v1/plc-connector/find/byName/{PLC}")
    check("정리: 커넥터 삭제", not (isinstance(b, dict) and b.get("name") == PLC), (s, b))
    fake.terminate()

print(f"\n{sum(results)}/{len(results)} PASS")
sys.exit(0 if all(results) else 1)
