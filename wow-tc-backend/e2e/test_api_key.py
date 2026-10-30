"""tc 1·2단계(보안 체인, API 클라이언트·키) e2e 확인. 비밀값은 출력하지 않는다."""
import json, os, re, subprocess, sys, threading, time, urllib.request, urllib.error
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402

R = os.path.dirname(os.path.abspath(__file__))
BASE = common.BASE
LOCAL_YML = common.LOCAL_YML
MYSQL = common.MYSQL
MEMURAI = common.REDIS_CLI
OPCODE = "plc.e2e.d100.read"

def db_password():
    for line in open(LOCAL_YML, encoding="utf-8"):
        m = re.match(r"\s*TC_DB_PASSWORD\s*:\s*(.*)", line)
        if m:
            return m.group(1).strip().strip('"').strip("'")

def sql(q):
    env = dict(os.environ, MYSQL_PWD=db_password())
    out = subprocess.run([MYSQL, "-uroot", "-N", "-B", "tc", "-e", q], env=env, capture_output=True, text=True)
    if out.returncode:
        raise RuntimeError(out.stderr)
    return out.stdout.strip()

def redis(*args):
    return subprocess.run([MEMURAI, *args], capture_output=True, text=True).stdout.strip()

TOKEN = common.admin_token()

def call(method, path, body=None, key=None, bearer=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if key is not None:
        req.add_header("X-API-Key", key)
    if bearer is not None:
        req.add_header("Authorization", bearer)
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            raw = r.read().decode()
            return r.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return e.code, json.loads(raw)
        except ValueError:
            return e.code, raw

def admin(method, path, body=None):
    return call(method, path, body, bearer=TOKEN)

seq = [0]
RUN = str(int(time.time()))
def plc(opcode=OPCODE, **extra):
    seq[0] += 1
    body = {"transactionId": f"t-auth-{RUN}-{seq[0]}", "messageId": f"m-auth-{RUN}-{seq[0]}", "opcode": opcode,
            "sender": "e2e", "receiver": "tc", "data": {}}
    body.update(extra)
    return body

results = []
def check(name, cond, detail=""):
    results.append((name, bool(cond)))
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else f"  -> {detail}"))

def strip_ts(b):
    return {k: v for k, v in b.items() if k != "timestamp"} if isinstance(b, dict) else b

CID = "e2e-auth"
# 깨끗한 상태로 — 이전 실행의 키를 지우고 클라이언트를 초기화(삭제된 ID 는 재사용 불가이므로 매번 새 ID)
CID = f"e2e-auth-{int(time.time())}"
s, b = admin("POST", "/api/v1/api-clients", {"clientId": CID, "name": "e2e auth", "allowedOpcodes": ["plc.e2e.*"]})
check("클라이언트 등록 201", s == 201, (s, b))
s, b = admin("POST", f"/api/v1/api-clients/{CID}/keys", {})
check("키 발급 201", s == 201 and b["apiKey"].startswith("tck_"), (s, b and {k: v for k, v in b.items() if k != 'apiKey'}))
K1, K1ID = b["apiKey"], b["keyId"]

# 인증 성공
s, b = call("POST", "/api/v1/ifsvc/request-and-reply", plc(), key=K1)
check("API 키 동기 요청 200", s == 200 and b.get("success") is True, (s, b))
tx_sync = f"t-auth-{RUN}-{seq[0]}"
s, b = call("POST", "/api/v1/ifsvc/request", plc(), key=K1)
check("API 키 비동기 요청 202", s == 202, (s, b))
time.sleep(1.5)
p = sql(f"select distinct PRINCIPAL from EXCHANGED_MESSAGE_AUDIT where TRX_ID='{tx_sync}'")
check("감사 PRINCIPAL = client:{id}", p == f"client:{CID}", p)

# 기존 JWT 호출 유지
s, b = call("POST", "/api/v1/ifsvc/request-and-reply", plc(), bearer=TOKEN)
check("관리자 JWT 동기 요청 200", s == 200 and b.get("success") is True, (s, b))
p = sql(f"select distinct PRINCIPAL from EXCHANGED_MESSAGE_AUDIT where TRX_ID='t-auth-{RUN}-{seq[0]}'")
check("감사 PRINCIPAL = user:tc-tester", p == "user:tc-tester", p)
s, b = admin("GET", "/api/v1/ifsvc/delivery")
check("JWT 관리 API 그대로 200", s == 200, (s, b))

# 인증 없음
s, b = call("POST", "/api/v1/ifsvc/request-and-reply", plc())
check("인증 없음 401", s == 401 and b.get("errorCode") == "ERR_401", (s, b))

# 인증 실패 — 본문이 모두 같아야 한다
k_parts = K1.split("_")
bad = {
    "형식 틀림": "not-a-key",
    "없는 KEY_ID": "tck_aaaaaaaaaaaa_" + k_parts[2],
    "해시 불일치": "tck_" + k_parts[1] + "_" + ("A" * 43),
}
bodies = {}
for name, k in bad.items():
    s, b = call("POST", "/api/v1/ifsvc/request-and-reply", plc(), key=k)
    bodies[name] = (s, strip_ts(b))
check("인증 실패 3종 모두 401·ERR_401_TC_API_KEY",
      all(v[0] == 401 and v[1].get("errorCode") == "ERR_401_TC_API_KEY" for v in bodies.values()), bodies)
check("인증 실패 3종 본문 동일", len({json.dumps(v[1], sort_keys=True) for v in bodies.values()}) == 1, bodies)

# 경로 범위 — API 키로 관리 API 는 안 됨
s, b = call("GET", "/api/v1/ifsvc/delivery", key=K1)
check("API 키로 관리 API 401", s == 401, (s, b))
s, b = call("GET", f"/api/v1/api-clients", key=K1)
check("API 키로 클라이언트 관리 API 401", s == 401, (s, b))

# 권한 — opcode
s, b = call("POST", "/api/v1/ifsvc/request-and-reply", plc(opcode="other.read"), key=K1)
check("허용 밖 opcode 403", s == 403 and b.get("errorCode") == "ERR_403_TC_OPCODE_FORBIDDEN", (s, b))

# 권한 — IP
admin("PUT", f"/api/v1/api-clients/{CID}", {"name": "e2e auth", "allowedOpcodes": ["plc.e2e.*"], "allowedIps": ["10.9.9.0/24"]})
s, b = call("POST", "/api/v1/ifsvc/request-and-reply", plc(), key=K1)
check("허용 밖 IP 403 (수정 즉시 반영)", s == 403 and b.get("errorCode") == "ERR_403_TC_API_CLIENT_IP", (s, b))
admin("PUT", f"/api/v1/api-clients/{CID}", {"name": "e2e auth", "allowedOpcodes": ["plc.e2e.*"], "allowedIps": ["127.0.0.0/8"]})
s, b = call("POST", "/api/v1/ifsvc/request-and-reply", plc(), key=K1)
check("허용 IP(127.0.0.0/8) 200", s == 200, (s, b))
s, b = admin("PUT", f"/api/v1/api-clients/{CID}", {"name": "x", "allowedIps": ["not-an-ip"]})
check("잘못된 IP 형식 400", s == 400, (s, b))

# 권한 — pushUrl
admin("PUT", f"/api/v1/api-clients/{CID}", {"name": "e2e auth", "allowedOpcodes": ["plc.e2e.*"],
                                             "allowedPushUrlPrefixes": ["http://allowed.local/"]})
s, b = call("POST", "/api/v1/ifsvc/request", plc(pushResult=True, pushUrl="http://evil.local/x"), key=K1)
check("허용 밖 pushUrl 400", s == 400, (s, b))

# 회전
s, b = admin("POST", f"/api/v1/api-clients/{CID}/keys", {"expiresInDays": 30})
check("두 번째 키 발급 201", s == 201, (s, b and b.get("keyId")))
K2, K2ID = b["apiKey"], b["keyId"]
s, b = call("POST", "/api/v1/ifsvc/request-and-reply", plc(), key=K2)
s1, _ = call("POST", "/api/v1/ifsvc/request-and-reply", plc(), key=K1)
check("두 키 모두 유효", s == 200 and s1 == 200, (s, s1))
s, b = admin("POST", f"/api/v1/api-clients/{CID}/keys", {})
check("세 번째 발급 409", s == 409, (s, b))
s, b = admin("POST", f"/api/v1/api-clients/{CID}/keys", {"expiresInDays": 9999})
check("수명 상한 초과 400", s == 400, (s, b))
s, b = admin("POST", f"/api/v1/api-clients/{CID}/keys/{K1ID}/revoke")
check("옛 키 폐기 200", s == 200 and b.get("status") == "REVOKED", (s, b))
s, b = call("POST", "/api/v1/ifsvc/request-and-reply", plc(), key=K1)
check("폐기한 키 즉시 401 (캐시 무효화)", s == 401, (s, b))
s, b = call("POST", "/api/v1/ifsvc/request-and-reply", plc(), key=K2)
check("새 키 200", s == 200, (s, b))

# 만료 — DB 로 만료 시각을 과거로 (캐시가 있으므로 클라이언트 수정으로 캐시를 지운다)
sql(f"update API_CLIENT_KEY set EXPIRES_AT = now() - interval 1 minute where KEY_ID='{K2ID}'")
admin("PUT", f"/api/v1/api-clients/{CID}", {"name": "e2e auth", "allowedOpcodes": ["plc.e2e.*"]})
s, b = call("POST", "/api/v1/ifsvc/request-and-reply", plc(), key=K2)
check("만료된 키 401", s == 401 and b.get("errorCode") == "ERR_401_TC_API_KEY", (s, b))
sql(f"update API_CLIENT_KEY set EXPIRES_AT = now() + interval 30 day where KEY_ID='{K2ID}'")
admin("PUT", f"/api/v1/api-clients/{CID}", {"name": "e2e auth", "allowedOpcodes": ["plc.e2e.*"]})

# 중지·재개
admin("POST", f"/api/v1/api-clients/{CID}/disable")
s, b = call("POST", "/api/v1/ifsvc/request-and-reply", plc(), key=K2)
check("중지된 클라이언트 401", s == 401, (s, b))
admin("POST", f"/api/v1/api-clients/{CID}/enable")
s, b = call("POST", "/api/v1/ifsvc/request-and-reply", plc(), key=K2)
check("재개 후 200", s == 200, (s, b))

# 단건 조회 — 해시·키 값 없음, 마지막 사용 기록
s, b = admin("GET", f"/api/v1/api-clients/{CID}")
txt = json.dumps(b)
check("단건 조회에 키 목록, 해시·키 값 없음", s == 200 and len(b["keys"]) == 2 and "keyHash" not in txt and "tck_" not in txt, (s, txt[:300]))
check("마지막 사용 시각 기록", any(k.get("lastUsedAt") for k in b["keys"]), b["keys"])
s, b = admin("GET", "/api/v1/api-clients")
mine = [c for c in b if c["clientId"] == CID]
check("목록에 활성 키 수 1", s == 200 and mine and mine[0]["activeKeyCount"] == 1, mine)

# 사용량 — 초당 한도
admin("PUT", f"/api/v1/api-clients/{CID}", {"name": "e2e auth", "allowedOpcodes": ["plc.e2e.*"], "rateLimitPerSec": 2})
codes = []
def fire(store):
    s, b = call("POST", "/api/v1/ifsvc/request", plc(), key=K2)
    store.append(b.get("errorCode") if s == 429 else s)
time.sleep(1.1)
ts = [threading.Thread(target=fire, args=(codes,)) for _ in range(6)]
[t.start() for t in ts]; [t.join() for t in ts]
check("초당 한도 2 → 429(RATE) 발생", codes.count("ERR_429_TC_RATE_LIMIT") >= 1 and codes.count(202) >= 1, codes)

# 사용량 — 동시 처리
admin("PUT", f"/api/v1/api-clients/{CID}", {"name": "e2e auth", "allowedOpcodes": ["plc.e2e.*"], "maxConcurrent": 1, "rateLimitPerSec": 1000})
codes = []
def fire_sync(store):
    s, b = call("POST", "/api/v1/ifsvc/request-and-reply", plc(), key=K2)
    store.append(b.get("errorCode") if s == 429 else s)
ts = [threading.Thread(target=fire_sync, args=(codes,)) for _ in range(8)]
[t.start() for t in ts]; [t.join() for t in ts]
check("동시 처리 한도 1 → 429(CONCURRENCY) 발생", codes.count("ERR_429_TC_CONCURRENCY") >= 1 and codes.count(200) >= 1, codes)
admin("PUT", f"/api/v1/api-clients/{CID}", {"name": "e2e auth", "allowedOpcodes": ["plc.e2e.*"]})

# 만료 임박 알림 — 6일 뒤 만료 키를 하나 두고 알림 Job 로그를 본다(기동 때 cron 을 30초로 바꿔 둔 경우만)
s, b = admin("POST", f"/api/v1/api-clients/{CID}/keys", {"expiresInDays": 6})
K3ID = b["keyId"] if s == 201 else None
check("만료 6일 키 발급 201", s == 201, (s, b and b.get("keyId")))
if os.environ.get("EXPIRY_CRON_FAST") and common.tc_log():  # tc 로그 경로가 있어야 알림 로그를 본다
    time.sleep(35)
    log = open(common.tc_log(), encoding="utf-8", errors="replace").read()
    check("만료 임박 알림 로그(7일 안)", f"keyId={K3ID}" in log and "만료 임박(7일 안)" in log, "log 없음")
if K3ID:
    admin("POST", f"/api/v1/api-clients/{CID}/keys/{K3ID}/revoke")

# 삭제 정책
s, b = admin("DELETE", f"/api/v1/api-clients/{CID}")
check("활성 키 있을 때 삭제 409", s == 409, (s, b))
admin("POST", f"/api/v1/api-clients/{CID}/keys/{K2ID}/revoke")
s, b = admin("DELETE", f"/api/v1/api-clients/{CID}")
check("키 폐기 후 삭제 204", s == 204, (s, b))
s, b = admin("POST", "/api/v1/api-clients", {"clientId": CID, "name": "again"})
check("삭제한 ID 재등록 409", s == 409, (s, b))
s, b = admin("GET", f"/api/v1/api-clients/{CID}")
check("삭제한 클라이언트 조회 404", s == 404, (s, b))

# 대입 시도 차단 — 마지막에(이 IP 가 5분 막힌다). 끝나면 Redis 키를 지운다.
codes = [call("POST", "/api/v1/ifsvc/request-and-reply", plc(), key="tck_bbbbbbbbbbbb_" + "B" * 43)[0] for _ in range(32)]
check("인증 실패가 1분 30회를 넘으면 429 (앞선 401 포함 집계)", 401 in codes and codes[-1] == 429 and codes.index(429) > 0, codes)
for ip in ("127.0.0.1",):
    redis("DEL", f"tc:apikey-block:{ip}", f"tc:apikey-fail:{ip}")

print()
print(f"{sum(1 for _, ok in results if ok)}/{len(results)} PASS")
sys.exit(0 if all(ok for _, ok in results) else 1)
