"""두 인스턴스 PLC 교환 전달(다중화 4단계, docs/plc-ha-design.md §3.1·§4·§5).

두 tc 를 소유권 관리를 켜고 같은 DB·Redis·비밀값으로 띄웠을 때만 돈다(아니면 건너뜀).
  TC_E2E_BASE_URL / TC_E2E_BASE_URL_B   두 인스턴스 주소
  TC_E2E_INTERNAL_SECRET_FILE           TC_INTERNAL_SECRET 값이 든 파일(서명 시험용)
  TC_E2E_FAILOVER=graceful|kill         주인 인스턴스를 정상 종료하거나 강제로 죽여 이어받기를 잰다(그 인스턴스는 내려간 채로 끝난다)
  TC_E2E_PID_A / TC_E2E_PID_B           두 인스턴스의 프로세스 ID(또는 그 값이 든 파일) — FAILOVER 때
내부 포트는 주 포트 + 100 이라고 본다(시험용 실행 스크립트와 같음)."""
import base64
import hashlib
import hmac
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
from urllib.parse import urlparse

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402

A = common.BASE
B = os.environ.get("TC_E2E_BASE_URL_B")
if not B:
    print("SKIP 두 번째 인스턴스 주소(TC_E2E_BASE_URL_B)가 없습니다")
    print("\n0/0 PASS")
    sys.exit(0)

SECRET_FILE = os.environ.get("TC_E2E_INTERNAL_SECRET_FILE")
SECRET = open(SECRET_FILE, encoding="utf-8").read().strip().encode() if SECRET_FILE else None
FAILOVER = os.environ.get("TC_E2E_FAILOVER", "")
HERE = os.path.dirname(os.path.abspath(__file__))


def redis(*args):
    return subprocess.run([common.REDIS_CLI, *args], capture_output=True, text=True).stdout.strip()


def login(base):
    uid, pw = common._admin_credentials()
    req = urllib.request.Request(base + "/api/auth/login", data=json.dumps({"id": uid, "password": pw}).encode(), method="POST")
    req.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req, timeout=30) as r:
        return r.headers.get("Authorization")


TOKENS = {A: login(A), B: login(B)}


def call(base, method, path, body=None, headers=None, raw=None, token=True):
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    req = urllib.request.Request(base + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", TOKENS.get(base, ""))
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=40) as r:
            text = r.read().decode()
            return r.status, (json.loads(text) if text.strip().startswith(("{", "[")) else text)
    except urllib.error.HTTPError as e:
        text = e.read().decode()
        try:
            return e.code, json.loads(text)
        except ValueError:
            return e.code, text
    except (urllib.error.URLError, ConnectionError) as e:
        return None, str(e)


results = []


def check(name, cond, detail=""):
    results.append(bool(cond))
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else f"  -> {str(detail)[:300]}"), flush=True)


SEQ = [0]


def read(base):
    SEQ[0] += 1
    tx = f"t-rmt-{int(time.time())}-{SEQ[0]}"
    s, b = call(base, "POST", "/api/v1/ifsvc/request-and-reply", {"transactionId": tx, "messageId": f"m-{SEQ[0]}",
                                                                 "opcode": "plc.e2e.d100.read", "sender": "e2e",
                                                                 "receiver": "tc", "data": {}})
    return tx, s, b


def internal(base):
    u = urlparse(base)
    return f"{u.scheme}://{u.hostname}:{u.port + 100}"


def signed(plc, body, ts=None, secret=None):
    ts = ts if ts is not None else int(time.time() * 1000)
    msg = f"{plc}\n{ts}\n{hashlib.sha256(body).hexdigest()}".encode()
    sig = base64.b64encode(hmac.new(secret or SECRET, msg, hashlib.sha256).digest()).decode()
    return {"X-TC-Instance": "e2e", "X-TC-Timestamp": str(ts), "X-TC-Signature": sig}


owner = redis("GET", "tc:plc-owner:plc-e2e")
owner_url = redis("GET", f"tc:instance:{owner}") if owner else ""
if not owner or not owner_url.startswith("http"):
    print(f"SKIP plc-e2e 의 주인·내부 주소가 없습니다(소유권 관리를 켜고 띄웠는지): owner={owner} url={owner_url}")
    print("\n0/0 PASS")
    sys.exit(0)
OWNER = A if urlparse(internal(A)).port == urlparse(owner_url).port else B  # 호스트 표기(localhost·127.0.0.1)는 다를 수 있다
OTHER = B if OWNER == A else A
print(f"주인 {OWNER}, 다른 인스턴스 {OTHER}")

# 1. 주인이 아닌 인스턴스로 온 요청 — 프레임만 넘기고 결과는 같다
tx, s, b = read(OTHER)
check("주인이 아닌 인스턴스로 요청 → 200, 값 있음", s == 200 and b.get("success") is True and "d100" in b.get("data", {}), (s, b))
tx2, s2, b2 = read(OWNER)
check("주인 인스턴스로 요청 → 200", s2 == 200 and b2.get("success") is True, (s2, b2))
conn_other = (call(OTHER, "GET", "/api/v1/plc-connector/find/byName/plc-e2e")[1] or {}).get("connection") or {}
check("다른 인스턴스의 조회에도 연결 상태(주인이 남긴 값)", conn_other.get("instance") == owner, conn_other)

# 2. 감사는 요청을 받은 인스턴스가 한 번만
DB_PW = None
for line in open(common.LOCAL_YML, encoding="utf-8"):
    if line.strip().startswith("TC_DB_PASSWORD"):
        DB_PW = line.split(":", 1)[1].strip().strip('"').strip("'")
rows = subprocess.run([common.MYSQL, "-uroot", "-N", "-B", "tc", "-e",
                       f"select MSG_TYPE, count(*) from EXCHANGED_MESSAGE_AUDIT where TRX_ID='{tx}' group by MSG_TYPE order by MSG_TYPE"],
                      env=dict(os.environ, MYSQL_PWD=DB_PW or ""), capture_output=True, text=True).stdout.split()
check("감사: REQUEST 1·RESPONSE 1(주인 쪽에 따로 남지 않음)", rows == ["REQUEST", "1", "RESPONSE", "1"], rows)

# 3. 내부 교환 엔드포인트 보호
body = json.dumps({"frame": "AAAA", "timeoutMs": 3000, "reqId": "e2e"}).encode()
path = "/internal/plc/plc-e2e/exchange"
s, b = call(internal(OTHER), "POST", path, raw=body, token=False)
check("서명 없는 내부 요청 → 401", s == 401, (s, b))
if SECRET:
    s, b = call(internal(OTHER), "POST", path, raw=body, headers=signed("plc-e2e", body), token=False)
    check("서명은 맞지만 주인이 아닌 인스턴스 → 409 ERR_409_TC_NOT_OWNER", s == 409 and b.get("errorCode") == "ERR_409_TC_NOT_OWNER", (s, b))
    s, b = call(internal(OWNER), "POST", path, raw=body, headers=signed("plc-e2e", body, ts=int(time.time() * 1000) - 60_000), token=False)
    check("시각이 30초 밖 → 401", s == 401, (s, b))
    s, b = call(internal(OWNER), "POST", path, raw=body, headers=signed("plc-e2e", body, secret=b"wrong"), token=False)
    check("다른 비밀값 → 401", s == 401, (s, b))
else:
    print("SKIP 서명 시험 — TC_E2E_INTERNAL_SECRET_FILE 이 없습니다")
s, _ = call(OWNER, "POST", path, raw=body, token=False)
check("주 포트로는 /internal 을 받지 않음 → 404", s == 404, s)
s, _ = call(internal(OWNER), "GET", "/api/health", token=False)
check("내부 포트로는 /internal 밖을 받지 않음 → 404", s == 404, s)

# 4. 이어받기
def pid_of(base):
    v = os.environ.get("TC_E2E_PID_A" if base == A else "TC_E2E_PID_B", "")
    return open(v).read().split()[0].lstrip("﻿") if os.path.isfile(v) else v


if FAILOVER in ("graceful", "kill"):
    pid = pid_of(OWNER)
    t0 = time.time()
    if FAILOVER == "graceful":
        subprocess.run(["java", os.path.join(HERE, "ShutdownViaJmx.java"), pid], capture_output=True)
    else:
        subprocess.run(["taskkill", "/F", "/PID", pid], capture_output=True)
    # 이어받을 때까지 다른 인스턴스로 계속 요청한다 — 실패가 시작된 때부터 다시 200 이 될 때까지가 끊긴 시간
    first_fail, ok_at, statuses = None, None, []
    while time.time() - t0 < 40:
        _, s, b = read(OTHER)
        statuses.append(s)
        now = time.time()
        if s != 200:
            first_fail = first_fail or now
        elif redis("GET", "tc:plc-owner:plc-e2e") not in ("", owner):
            ok_at = now
            break
        time.sleep(0.3)
    gap = (ok_at - (first_fail or ok_at)) if ok_at else None
    survivor_id = redis("GET", "tc:plc-owner:plc-e2e")
    limit = 6 if FAILOVER == "graceful" else 18
    check(f"{FAILOVER}: 다른 인스턴스가 이어받음(임대 주인 바뀜)", survivor_id and survivor_id != owner, survivor_id)
    check(f"{FAILOVER}: 요청이 끊긴 시간 {gap if gap is None else round(gap, 1)}초 ≤ {limit}초", gap is not None and gap <= limit,
          statuses[-10:])
    if FAILOVER == "graceful":
        check("정상 종료한 인스턴스는 임대·인스턴스 목록을 반납", redis("EXISTS", f"tc:instance:{owner}") == "0")
else:
    print("SKIP 이어받기 — TC_E2E_FAILOVER=graceful|kill 로 실행하면 주인 인스턴스를 내려 시험한다")

print(f"\n{sum(results)}/{len(results)} PASS")
sys.exit(0 if all(results) else 1)
