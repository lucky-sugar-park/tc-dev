"""RAW → POINTS 변환 도구(PLC 일반화 4단계) — docs/plc-protocol-design.md §8·§15.

- 미리보기(dryRun, 기본)는 바꾸지 않고 점·경고·옛 응답 인터페이스를 돌려준다. 적용하면 mode=POINTS 가 되고 같은 opcode·같은 data 키·같은 값.
- 헤더·데이터 속성은 남는다 — mode 를 RAW 로 되돌리면 예전대로 돈다.
- 알아볼 수 없는 정의(워드 경계가 아닌 속성)는 400 과 사유, 이미 POINTS 면 400, 없는 인터페이스는 404.
전제: vplc plc-e2e(MC 3E BINARY, D 메모리) 게시됨(setup_e2e.py). D300·D301 을 쓴다."""
import copy
import json
import os
import sys
import time
import urllib.error
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
TOKEN = common.admin_token()
RUN = str(int(time.time()))
M = "/api/v1/ifsvc/management"


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
    return call("POST", "/api/v1/ifsvc/request-and-reply", {"transactionId": f"t-cv-{RUN}-{SEQ[0]}", "messageId": f"m-{SEQ[0]}",
                                                           "opcode": opcode, "sender": "e2e", "receiver": "tc", "data": data or {}})


def find(name):
    return call("GET", f"{M}/find/byName/{name}")[1]


REQ = json.load(open(os.path.join(HERE, "fixture-if-read.json"), encoding="utf-8"))
REP = json.load(open(os.path.join(HERE, "fixture-if-read-reply.json"), encoding="utf-8"))
DATA = [{"interfaceId": 0, "name": "w1", "order": 1, "type": "SHORT", "length": 2, "description": "D300"},
        {"interfaceId": 0, "name": "w2", "order": 2, "type": "SHORT", "length": 2, "description": "D301"}]


def raw_pair(name, if_type, start_hex, data_props, words="0200"):
    """RAW 요청 인터페이스와 응답 인터페이스 — 읽기는 응답에 데이터 속성, 쓰기는 요청에"""
    req, rep = copy.deepcopy(REQ), copy.deepcopy(REP)
    req.update({"name": name, "replyName": name + ".reply", "interfaceType": if_type, "description": "e2e convert"})
    for h in req["headerProps"]:
        if h["name"] == "command":
            h["strValue"] = "0104" if if_type == "READ" else "0114"
        if h["name"] == "startAddress":
            h["strValue"] = start_hex
        if h["name"] == "wordCount":
            h["strValue"] = words
    rep.update({"name": name + ".reply", "description": "e2e convert reply"})
    length = sum(p["length"] for p in data_props)
    if if_type == "READ":
        rep.update({"dataProps": data_props, "dataLength": length})
        for h in rep["headerProps"]:
            if h["name"] == "respDataLen":
                h["strValue"] = f"{length + 2:02X}00"
    else:
        req.update({"dataProps": data_props, "dataLength": length})
        rep.update({"dataProps": [], "dataLength": 0})
        for h in rep["headerProps"]:
            if h["name"] == "respDataLen":
                h["strValue"] = "0200"
    out = []
    for d in (rep, req):
        s, b = call("POST", f"{M}/register", d)
        out.append((d["name"], s, b))
        created.append(d["name"])
    return out


created = []
READ = f"e2e.cv.read.{RUN}"
WRITE = f"e2e.cv.write.{RUN}"
ODD = f"e2e.cv.odd.{RUN}"
try:
    regs = raw_pair(READ, "READ", "2C0100", DATA) + raw_pair(WRITE, "WRITE", "2C0100", DATA)
    check("RAW 읽기·쓰기 인터페이스(D300..D301)와 응답 인터페이스 등록", all(s == 200 for _, s, _ in regs), regs)
    a, b = int(RUN) % 20000, -321
    s, w = plc(WRITE, {"w1": a, "w2": b})
    s2, r = plc(READ)
    check("RAW 쓰기 → RAW 읽기 같은 값", s == 200 and s2 == 200 and r.get("data") == {"w1": a, "w2": b}, (s, w, s2, r))

    # 1. 미리보기
    s, p = call("POST", f"{M}/convert-to-points/byName/{READ}")
    check("미리보기(기본 dryRun): 점 w1=D300·w2=D301 INT16, 적용 안 함, 옛 응답 인터페이스", s == 200 and p.get("dryRun") is True
          and p.get("applied") is False and [(x["name"], x["address"], x["type"]) for x in p.get("points", [])]
          == [("w1", "D300", "INT16"), ("w2", "D301", "INT16")] and p.get("replyName") == READ + ".reply" and p.get("replyUsedBy") == [],
          (s, p))
    check("미리보기 뒤에도 RAW", find(READ).get("mode") == "RAW", find(READ).get("mode"))
    iid = find(READ)["id"]
    s, p2 = call("POST", f"{M}/{iid}/convert-to-points?dryRun=true")
    check("id 로도 같은 미리보기", s == 200 and p2.get("points") == p.get("points"), (s, p2))

    # 2. 적용 — 같은 opcode·같은 키·같은 값
    s, p = call("POST", f"{M}/convert-to-points/byName/{READ}?dryRun=false")
    got = find(READ)
    check("적용 → mode POINTS, 점 2개, 헤더·데이터 속성은 남음", s == 200 and p.get("applied") is True and got.get("mode") == "POINTS"
          and len(got.get("points") or []) == 2 and len(got.get("headerProps") or []) > 0, (s, p, got.get("mode")))
    s, r = plc(READ)
    check("바꾼 뒤 같은 opcode 읽기 → 같은 data 와 plcStatus", s == 200 and r.get("data") == {"w1": a, "w2": b}
          and (r.get("plcStatus") or {}).get("code") == 0, (s, r))
    s, p = call("POST", f"{M}/convert-to-points/byName/{WRITE}?dryRun=false")
    check("쓰기 인터페이스 적용(데이터 속성은 요청 인터페이스에서)", s == 200 and [x["address"] for x in p.get("points", [])] == ["D300", "D301"], (s, p))
    c, d = (a + 1) % 20000, 4321
    s, w = plc(WRITE, {"w1": c, "w2": d})
    s2, r = plc(READ)
    check("바꾼 쓰기 → 바꾼 읽기 같은 값", s == 200 and w.get("success") is True and r.get("data") == {"w1": c, "w2": d}, (s, w, r))
    s, b2 = call("POST", f"{M}/convert-to-points/byName/{READ}")
    check("이미 POINTS → 400", s == 400, (s, b2))

    # 3. 되돌리기 — mode RAW
    info = find(WRITE)
    info["mode"] = "RAW"
    s, _ = call("POST", f"{M}/update", info)
    e = (c + 1) % 20000
    s2, w = plc(WRITE, {"w1": e, "w2": 5})
    s3, r = plc(READ)
    check("쓰기를 RAW 로 되돌림 → 예전 정의로 쓰기 200, 값 일치", s == 200 and s2 == 200 and find(WRITE).get("mode") == "RAW"
          and r.get("data") == {"w1": e, "w2": 5}, (s, s2, w, r))

    # 4. 바꾸지 않는 정의
    odd = [{"interfaceId": 0, "name": "s1", "order": 1, "type": "STRING", "length": 1, "description": ""},
           {"interfaceId": 0, "name": "s2", "order": 2, "type": "STRING", "length": 3, "description": ""}]
    regs = raw_pair(ODD, "READ", "2C0100", odd)
    s, b2 = call("POST", f"{M}/convert-to-points/byName/{ODD}")
    check("워드 경계가 아닌 속성 → 400 과 사유", all(x[1] == 200 for x in regs) and s == 400 and "워드 경계" in str(b2), (regs, s, b2))
    s, b2 = call("POST", f"{M}/convert-to-points/byName/no.such.{RUN}")
    check("없는 인터페이스 → 404", s == 404, (s, b2))
    s, b2 = call("POST", f"{M}/convert-to-points/byName/{READ}.reply")
    check("응답(REPLY) 인터페이스 → 400", s == 400, (s, b2))
finally:
    for name in reversed(created):
        call("DELETE", f"{M}/delete/byName/{name}")

print(f"\n{sum(results)}/{len(results)} PASS")
sys.exit(0 if all(results) else 1)
