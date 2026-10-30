"""헬스 체크와 정상 종료 순서 시험. 전제: 가짜 PLC(:15100, DELAY=1, 5초), tc 를 SPRING_APPLICATION_ADMIN_ENABLED=true 로 띄움."""
import json, os, re, subprocess, threading, time, urllib.request, urllib.error
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402

R = os.path.dirname(os.path.abspath(__file__))
BOOT = R
BASE = common.BASE
TOKEN = common.admin_token()
LOCAL_YML = common.LOCAL_YML
MYSQL = common.MYSQL

def sql(q):
    pw = [re.match(r"\s*TC_DB_PASSWORD\s*:\s*(.*)", l).group(1).strip().strip('"').strip("'")
          for l in open(LOCAL_YML, encoding="utf-8") if re.match(r"\s*TC_DB_PASSWORD\s*:", l)][0]
    return subprocess.run([MYSQL, "-uroot", "-N", "-B", "tc", "-e", q], env=dict(os.environ, MYSQL_PWD=pw),
                          capture_output=True, text=True, encoding="utf-8", errors="replace").stdout.strip()

def call(method, path, body=None, token=TOKEN, timeout=40):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", token)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            raw = r.read().decode()
            return r.status, (json.loads(raw) if raw.strip().startswith(("{", "[")) else raw)
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return e.code, json.loads(raw)
        except ValueError:
            return e.code, raw
    except Exception as e:
        return -1, str(e)

results = []
def check(name, cond, detail=""):
    results.append(bool(cond))
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else f"  -> {str(detail)[:400]}"), flush=True)

s, b = call("GET", "/api/health", token=None)
check("헬스 체크: 인증 없이 200 UP (db·redis UP)", s == 200 and b.get("status") == "UP" and b.get("db") == "UP" and b.get("redis") == "UP", (s, b))
s, b = call("POST", "/api/health", token=None)
check("헬스 체크 외 메서드는 공개 아님(401)", s == 401, (s, b))

call("GET", "/api/v1/plc-connector/control/publish/byName/plc-fake")
time.sleep(2)

# 5초 걸리는 PLC 요청을 보내 둔 채 정상 종료를 요청한다
out = {}
def slow():
    out["r"] = call("POST", "/api/v1/ifsvc/request-and-reply", {"transactionId": f"t-sd-{int(time.time())}", "messageId": "m",
                    "opcode": "plc.fake.read", "sender": "e2e", "receiver": "tc", "data": {}})
t = threading.Thread(target=slow); t.start()
time.sleep(1.0)
pid = common.tc_pid()
r = subprocess.run(["java", os.path.join(BOOT, "ShutdownViaJmx.java"), pid], capture_output=True, text=True, encoding="utf-8", errors="replace")
print(r.stdout.strip(), r.stderr.strip()[:300])
t.join(30)
s, b = out.get("r", (None, None))
check("종료 중에도 진행 중이던 PLC 요청은 끝까지 처리(200)", s == 200 and b.get("success") is True, (s, b))
time.sleep(10)

log = open(common.tc_log(), encoding="utf-8", errors="replace").read()
i1, i2, i3 = log.find("Commencing graceful shutdown"), log.find("Graceful shutdown complete"), log.find("PLC 연결을 닫습니다(앱 종료)")
check("종료 순서: 웹 서버 graceful → 완료 → PLC 연결 닫기", -1 < i1 < i2 < i3, (i1, i2, i3))
st = sql("select name, status_1 from plc_connector where name in ('plc-fake','plc-e2e') order by name")
check("정상 종료 뒤에도 커넥터는 PUBLISHED (다음 기동 때 다시 올라옴)", st.count("PUBLISHED") == 2, st)
print(f"\n{sum(results)}/{len(results)} PASS")
sys.exit(0 if all(results) else 1)
