"""PLC 연결 상태 분리(다중화 2단계, docs/plc-ha-design.md §3.4).

- 운영자 의도는 DB status1, 실제 연결은 Redis tc:plc-state:{이름} — 조회 API 의 connection 으로 합쳐 보여 준다.
- 어댑터 이벤트는 DB 상태를 바꾸지 않는다(이력만 남김).
전제: vplc plc-e2e 게시됨(setup_e2e.py)."""
import json
import os
import re
import subprocess
import sys
import time
from datetime import datetime
import urllib.error
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402

TOKEN = common.admin_token()
PLC = "plc-e2e"


def db_password():
    for line in open(common.LOCAL_YML, encoding="utf-8"):
        m = re.match(r"\s*TC_DB_PASSWORD\s*:\s*(.*)", line)
        if m:
            return m.group(1).strip().strip('"').strip("'")
    raise SystemExit("application-local.yml 에 TC_DB_PASSWORD 가 없습니다")


DB_PW = db_password()


def sql(q):
    out = subprocess.run([common.MYSQL, "-uroot", "-N", "-B", "tc", "-e", q], env=dict(os.environ, MYSQL_PWD=DB_PW),
                         capture_output=True, text=True, encoding="utf-8", errors="replace")
    return out.stdout.strip()


def redis(*args):
    return subprocess.run([common.REDIS_CLI, *args], capture_output=True, text=True).stdout.strip()


def call(method, path):
    req = urllib.request.Request(common.BASE + path, method=method)
    req.add_header("Authorization", TOKEN)
    try:
        with urllib.request.urlopen(req, timeout=40) as r:
            raw = r.read().decode()
            return r.status, (json.loads(raw) if raw.strip().startswith(("{", "[")) else raw)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


results = []


def check(name, cond, detail=""):
    results.append(bool(cond))
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else f"  -> {str(detail)[:300]}"), flush=True)


def connector():
    return call("GET", f"/api/v1/plc-connector/find/byName/{PLC}")[1]


# 1. 게시된 커넥터 — 연결 상태가 함께 온다
c = connector()
conn = c.get("connection") or {}
check("조회 응답의 connection: RUNNING, 채널 1 이상, 연결한 인스턴스", conn.get("operStatus") == "RUNNING"
      and conn.get("activeChannels", 0) >= 1 and bool(conn.get("instance")), conn)
s, states = call("GET", "/api/v1/plc-connector/connection-states")
check("connection-states 에 plc-e2e", s == 200 and any(x.get("plcConnectorName") == PLC for x in states), (s, states))
ttl = int(redis("PTTL", f"tc:plc-state:{PLC}") or -2)
check("Redis 상태 키에 만료(15초 이하)가 있다", 0 < ttl <= 15000, ttl)
time.sleep(6)
ttl2 = int(redis("PTTL", f"tc:plc-state:{PLC}") or -2)
updated = (connector().get("connection") or {}).get("updatedAt")
age = time.time() - datetime.fromisoformat(updated.replace("Z", "+00:00")).timestamp() if updated else 99
check("5초마다 갱신 — 만료가 다시 늘고 updatedAt 이 최근", ttl2 > 9000 and age < 6.5, (ttl2, updated))

# 2. 해제·게시 — 의도는 DB, 연결 상태는 Redis
since = sql("select date_format(now(3), '%Y-%m-%d %H:%i:%s.%f')")
s, _ = call("POST", f"/api/v1/plc-connector/control/release/byName/{PLC}")
c = connector()
check("해제 → status1 RELEASED, connection 없음, Redis 키 지워짐", s == 200 and c.get("status1") == "RELEASED"
      and c.get("connection") is None and redis("EXISTS", f"tc:plc-state:{PLC}") == "0", (s, c.get("status1"), c.get("connection")))
s, _ = call("POST", f"/api/v1/plc-connector/control/publish/byName/{PLC}")
c = connector()
conn = c.get("connection") or {}
check("다시 게시 → status1 PUBLISHED, connection 채널 1 이상", s == 200 and c.get("status1") == "PUBLISHED"
      and conn.get("activeChannels", 0) >= 1, (s, c.get("status1"), conn))

# 3. 이벤트는 이력만 남기고 DB 상태를 고치지 않는다 — 예전의 START_UPDATE·SHUTDOWN_UPDATE 이력이 없다
rows = sql(f"select EVENT_TYPE, CAUSE from PLC_CONNECTION_HIST where PLC_CONNECTOR_NAME='{PLC}' and EVENT_TIMESTAMP >= '{since}' order by ID")
check("해제·게시 이력은 SHUTDOWN·START(·CONNECT)만, *_UPDATE 없음",
      "SHUTDOWN" in rows and "START" in rows and "_UPDATE" not in rows, rows)

print(f"\n{sum(results)}/{len(results)} PASS")
sys.exit(0 if all(results) else 1)
