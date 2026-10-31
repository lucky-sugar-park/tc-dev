"""PLC 프로토콜 일반화 1단계 — POINTS 인터페이스(docs/plc-protocol-design.md §4·§11).

- 점(이름·주소·형)으로 정의한 READ 가 같은 주소의 RAW 인터페이스와 같은 값을 돌려준다.
- WRITE(정수·32비트·실수·문자열·배열, readBack), 비트 디바이스 읽기, plcStatus, 값 기반 변경 감지.
- 등록 검증(주소·형·커넥터 설정), 쓰기 값 검증, RAW 결함 M2(READ 에 쓰기 명령) 거절.
전제: vplc plc-e2e(MC 3E BINARY) 게시됨(setup_e2e.py)."""
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
HERE = os.path.dirname(os.path.abspath(__file__))


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
    return call("POST", "/api/v1/ifsvc/request-and-reply", {"transactionId": f"t-pts-{RUN}-{SEQ[0]}", "messageId": f"m-{SEQ[0]}",
                                                           "opcode": opcode, "sender": "e2e", "receiver": "tc", "data": data or {}})


def register(name, if_type, points, read_back=False):
    return call("POST", "/api/v1/ifsvc/management/register", {
        "name": name, "plcName": PLC, "interfaceType": if_type, "mode": "POINTS", "points": points,
        "readBack": read_back, "use": True, "sync": True, "reply": True, "description": "e2e points"})


def find(name):
    return call("GET", f"/api/v1/ifsvc/management/find/byName/{name}")[1]


def vplc_list():
    try:
        req = urllib.request.Request(common.VPLC_BASE + "/api/v1/vplc/management/search", data=b"{}", method="POST")
        req.add_header("Content-Type", "application/json")
        with urllib.request.urlopen(req, timeout=10) as r:
            return json.loads(r.read().decode())
    except (urllib.error.URLError, ValueError):
        return []


created = []
READ = f"e2e.pts.read.{RUN}"
WRITE = f"e2e.pts.write.{RUN}"
BITS = f"e2e.pts.bits.{RUN}"
try:
    # 1. READ — 같은 주소의 RAW 인터페이스(plc.e2e.d100.read)와 같은 값
    s, b = register(READ, "READ", [{"name": "d100", "address": "D100", "type": "INT16"},
                                   {"name": "d101", "address": "D101", "type": "INT16"},
                                   {"name": "pair", "address": "D100", "type": "INT16", "count": 2}])
    check("POINTS READ 등록 200", s == 200, (s, b))
    created.append(READ)
    info = find(READ)
    check("조회: mode POINTS, 점 3개 순서대로", isinstance(info, dict) and info.get("mode") == "POINTS"
          and [p["name"] for p in info.get("points", [])] == ["d100", "d101", "pair"], info)
    s, raw = plc("plc.e2e.d100.read")
    s2, pts = plc(READ)
    check("POINTS 읽기 200, success", s2 == 200 and pts.get("success") is True, (s2, pts))
    check("RAW 와 같은 값(d100·d101), 배열 점", pts.get("data", {}).get("d100") == raw.get("data", {}).get("d100")
          and pts["data"].get("d101") == raw["data"].get("d101") and pts["data"].get("pair") == [raw["data"]["d100"], raw["data"]["d101"]],
          (raw.get("data"), pts.get("data")))
    check("plcStatus: MELSEC_MC, code 0 (POINTS·RAW 모두)", pts.get("plcStatus") == {"protocol": "MELSEC_MC", "code": 0, "text": "OK"}
          and raw.get("plcStatus", {}).get("code") == 0, (pts.get("plcStatus"), raw.get("plcStatus")))
    s3, again = plc(READ)
    check("값이 같으면 changed=false(값 비교)", again.get("changed") is False, again)

    # 2. WRITE — 여러 형, readBack
    points = [{"name": "i16", "address": "D3000", "type": "INT16"},
              {"name": "i32", "address": "D3001", "type": "INT32"},
              {"name": "f32", "address": "D3003", "type": "FLOAT32"},
              {"name": "txt", "address": "D3005", "type": "STRING", "length": 6},
              {"name": "arr", "address": "D3010", "type": "UINT16", "count": 3}]
    s, b = register(WRITE, "WRITE", points, read_back=True)
    check("POINTS WRITE 등록 200(readBack)", s == 200, (s, b))
    created.append(WRITE)
    values = {"i16": -123, "i32": 123456789, "f32": 1.5, "txt": "AB12", "arr": [1, 65535, 7]}
    s, w = plc(WRITE, values)
    check("쓰기 200, readBack 값이 쓴 값과 같다", s == 200 and w.get("success") is True and w.get("data") == values, (s, w))
    rd = f"e2e.pts.rd.{RUN}"
    s, _ = register(rd, "READ", points)
    created.append(rd)
    s, r = plc(rd)
    check("다른 READ 인터페이스로 읽어도 같은 값", r.get("data") == values, r.get("data"))

    # 3. 비트 디바이스 — 워드로 읽어 비트를 꺼낸다(값은 vplc 메모리 그대로). vplc 는 만들지 않은 메모리 종류에는 답하지 않으므로
    #    (실제 PLC 는 오류 종료 코드로 답한다) M 메모리가 있을 때만 본다. 비트 꺼내기 자체는 단위 시험(MelsecDriverTest)
    vplcs = vplc_list()
    if any("M*" in (v.get("memoryTypeList") or []) and v.get("startPort") == 15000 for v in vplcs):
        s, b = register(BITS, "READ", [{"name": "m", "address": "M10", "type": "BOOL", "count": 3}])
        created.append(BITS)
        s, r = plc(BITS)
        m = r.get("data", {}).get("m")
        check("비트 디바이스 읽기 200, BOOL 3개", s == 200 and isinstance(m, list) and len(m) == 3 and all(isinstance(x, bool) for x in m), (s, r))
    else:
        print("SKIP 비트 디바이스 — vplc(15000)에 M 메모리가 없습니다")

    # 4. 쓰기 값 검증
    s, b = plc(WRITE, {"i16": 1})
    check("쓰기 값이 빠지면 400 ERR_400", s == 400 and b.get("errorCode") == "ERR_400" and "쓰기 값이 없습니다" in b.get("errorMessage", ""), (s, b))
    s, b = plc(WRITE, dict(values, i16=40000))
    check("범위 밖 값 400", s == 400 and "범위" in b.get("errorMessage", ""), (s, b))

    # 5. 등록 검증 — 드라이버가 주소·형을 본다
    for label, pts in (("모르는 디바이스", [{"name": "x", "address": "Q100", "type": "INT16"}]),
                       ("워드 디바이스 BOOL", [{"name": "x", "address": "D100", "type": "BOOL"}]),
                       ("비트 디바이스 INT16", [{"name": "x", "address": "M0", "type": "INT16"}]),
                       ("STRING 길이 없음", [{"name": "x", "address": "D0", "type": "STRING"}]),
                       ("이름 겹침", [{"name": "x", "address": "D0", "type": "INT16"}, {"name": "x", "address": "D1", "type": "INT16"}]),
                       ("점 없음", [])):
        s, b = register(f"e2e.pts.bad.{RUN}", "READ", pts)
        check(f"등록 거절 400 — {label}", s == 400, (s, b))
    s, b = call("POST", "/api/v1/ifsvc/management/register", {"name": f"e2e.pts.nocon.{RUN}", "plcName": "no-such-plc", "interfaceType": "READ",
                                                              "mode": "POINTS", "points": [{"name": "x", "address": "D0", "type": "INT16"}]})
    check("등록 거절 400 — 커넥터 없음", s == 400 and "커넥터" in str(b), (s, b))

    # 6. 커넥터 프로토콜·설정 — 드라이버가 검증하고 기본값을 채운다
    base = find("plc-e2e") if False else call("GET", f"/api/v1/plc-connector/find/byName/{PLC}")[1]
    con = {k: v for k, v in base.items() if k not in ("id", "connection", "createdBy", "createdDate", "createdDateAsString",
                                                      "updatedBy", "updatedDate", "updatedDateAsString")}
    con.update({"name": f"e2e-proto-{RUN}", "ports": [15999], "published": False, "status1": "REGISTERED", "status2": "STOPPED"})
    s, b = call("POST", "/api/v1/plc-connector/register", dict(con, protocol="MELSEC_MC", settings={"frame": "1E_BINARY"}))
    check("커넥터 설정 거절 400 — 없는 프레임", s == 400 and "frame" in str(b), (s, b))
    s, b = call("POST", "/api/v1/plc-connector/register", dict(con, protocol="OPC_UA", settings={}))
    check("커넥터 거절 400 — 아직 없는 프로토콜", s == 400 and "프로토콜" in str(b), (s, b))
    s, b = call("POST", "/api/v1/plc-connector/register", dict(con, protocol="MELSEC_MC", settings={"ioNo": "0x03E0"}))
    got = call("GET", f"/api/v1/plc-connector/find/byName/{con['name']}")[1]
    check("커넥터 등록 → 설정 기본값 채움(frame·pcNo), 16진 입력", s == 200 and got.get("protocol") == "MELSEC_MC"
          and got.get("settings", {}).get("frame") == "3E_BINARY" and got["settings"].get("ioNo") == 0x03E0
          and got["settings"].get("pcNo") == 255, (s, got.get("settings")))
    call("DELETE", f"/api/v1/plc-connector/delete/byName/{con['name']}")

    # 7. RAW 결함 M2 — READ 인터페이스에 쓰기 명령(1401)이면 PLC 로 보내지 않고 409
    bad = json.load(open(os.path.join(HERE, "fixture-if-read.json"), encoding="utf-8"))
    bad["name"] = f"e2e.raw.badcmd.{RUN}"
    for h in bad["headerProps"]:
        if h["name"] == "command":
            h["strValue"] = "0114"
    s, b = call("POST", "/api/v1/ifsvc/management/register", bad)
    created.append(bad["name"])
    s, b = plc(bad["name"])
    check("RAW READ 에 쓰기 명령 → 409 ERR_409_TC_INTERFACE_CONFIG", s == 409 and b.get("errorCode") == "ERR_409_TC_INTERFACE_CONFIG", (s, b))
finally:
    for name in created:
        call("DELETE", f"/api/v1/ifsvc/management/delete/byName/{name}")
    left = [n for n in created if isinstance(find(n), dict) and find(n).get("name")]
    check("정리: POINTS 인터페이스 삭제(점도 함께)", not left, left)

print(f"\n{sum(results)}/{len(results)} PASS")
sys.exit(0 if all(results) else 1)
