"""기록 보관 e2e (docs/data-retention.md) — 주기 수집 Job 의 auditMode=FAILURE_ONLY(성공은 송수신 기록 없음, 실패는 REQUEST 와 함께),
저장 검사, 정리 Job 지금 실행(없는 Job 404·예약되지 않은 Job 409), 감사 삭제 API 없음, 기본 정리 Job.
전제: setup_e2e(plc-e2e·plc.e2e.d100.read), vplc plc-e2e 게시됨. 약 25초 걸린다.
DB 이름은 TC_E2E_DB(기본 tc) — 시험 인스턴스가 다른 DB 를 쓰면 맞춘다."""
import json, os, re, subprocess, sys, time, urllib.request, urllib.error
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402

BASE = common.BASE
TOKEN = common.admin_token()
DB = os.environ.get("TC_E2E_DB", "tc")
RUN = str(int(time.time()))
M = "/api/v1/schedule-job/management"
MON = "/api/v1/schedule-job/monitoring"
PLC_JOB = "com.wow.be.tc.schedule.jobs.PlcGeneralScheduleJob"
IFACE = "plc.e2e.d100.read"


def sql(q):
    pw = [re.match(r"\s*TC_DB_PASSWORD\s*:\s*(.*)", l).group(1).strip().strip('"').strip("'")
          for l in open(common.LOCAL_YML, encoding="utf-8") if re.match(r"\s*TC_DB_PASSWORD\s*:", l)][0]
    out = subprocess.run([common.MYSQL, "-uroot", "-N", "-B", DB, "-e", q], env=dict(os.environ, MYSQL_PWD=pw),
                         capture_output=True, text=True, encoding="utf-8", errors="replace")
    return out.stdout.strip()


def call(method, path, body=None, timeout=40):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    req.add_header("Authorization", TOKEN)
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


results = []


def check(name, cond, detail=""):
    results.append(bool(cond))
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else f"  -> {detail}"), flush=True)


def prop(k, v, t="string"):
    return {"entryKey": k, "entryValue": v, "entryValueType": t}


def job(name, props, interval=2000):
    return {"name": name, "use": True, "groupName": "E2E", "jobClassName": PLC_JOB, "triggerType": "simple",
            "startDelay": 500, "repeatInterval": interval, "description": "e2e retention", "jobDetails": props}


def history(name):
    return call("POST", f"{M}/exec-histories/query", {"jobName": name, "page": 0, "size": 50})[1]["items"]


def trx_of(remark):
    m = re.search(r"trx ([^)\s]+)", remark or "")
    return m.group(1) if m else None


# ── 저장 검사 ──
s, b = call("POST", f"{M}/register", job(f"e2e-rt-bad-{RUN}", [prop("interfaceName", IFACE), prop("auditMode", "SOMETIMES")]))
check("auditMode 가 ALL·FAILURE_ONLY 가 아니면 400", s == 400 and "auditMode" in json.dumps(b, ensure_ascii=False), (s, b))
s, kinds = call("GET", f"{M}/job-kinds")
spec = next((p for k in kinds if k["kind"] == "PLC" for p in k["properties"] if p["key"] == "auditMode"), None)
check("Job 종류: PLC 에 auditMode(선택, ALL·FAILURE_ONLY)", spec and not spec["required"] and spec["options"] == ["ALL", "FAILURE_ONLY"], spec)

# ── 성공은 남기지 않는다 ──
fo = f"e2e-rt-fo-{RUN}"
s, b = call("POST", f"{M}/register", job(fo, [prop("interfaceName", IFACE), prop("auditMode", "FAILURE_ONLY")]))
check("FAILURE_ONLY Job 등록 200", s == 200, (s, b))
call("POST", f"{MON}/rescheduleJob/{fo}")
time.sleep(6)
ok = [h for h in history(fo) if h["executionResult"] == "SUCCESS"]
check("FAILURE_ONLY: 성공 실행이 있다", len(ok) >= 2, history(fo))
check("FAILURE_ONLY: 비고에 '송수신 기록 생략'", ok and all("생략" in h["remark"] for h in ok), [h["remark"] for h in ok])
trxs = [trx_of(h["remark"]) for h in ok]
n = sql("select count(*) from exchanged_message_audit where trx_id in (" + ",".join(f"'{t}'" for t in trxs) + ")") if trxs else "x"
check("FAILURE_ONLY: 성공한 거래는 송수신 기록이 없다", n == "0", (n, trxs))

# ── 실패는 REQUEST 와 함께 남긴다 ──
s, b = call("POST", "/api/v1/plc-connector/control/pause/byName/plc-e2e")
check("커넥터 멈춤 200", s == 200, (s, b))
time.sleep(5)
bad = [h for h in history(fo) if h["executionResult"] == "FAIL"]
check("FAILURE_ONLY: 커넥터를 멈추면 실패 실행", bad, history(fo)[:3])
t = trx_of(bad[0]["remark"]) if bad else None
types = sql(f"select group_concat(msg_type order by id) from exchanged_message_audit where trx_id='{t}'") if t else ""
check("FAILURE_ONLY: 실패한 거래는 REQUEST 와 ERROR(또는 RESPONSE)가 남는다",
      types.startswith("REQUEST,") and ("ERROR" in types or "RESPONSE" in types), (t, types, bad[:1]))
call("POST", "/api/v1/plc-connector/control/resume/byName/plc-e2e")
call("DELETE", f"{M}/delete/byName/{fo}")

# ── 지금 실행 ──
cl = "cleanup-schedule-job-exec-hist"
check("기본 정리 Job 이 있다(기동 때 만듦)", sql(f"select count(*) from schedule_job where name='{cl}'") == "1")
before = len(history(cl))
s, b = call("POST", f"{MON}/runScheduleJobNow/{cl}")
check("지금 실행 200", s == 200, (s, b))
time.sleep(3)
after = history(cl)
check("지금 실행: 실행 이력이 하나 늘고 SUCCESS", len(after) == before + 1 and after[0]["executionResult"] == "SUCCESS", after[:1])
s, b = call("POST", f"{MON}/runScheduleJobNow/no-such-e2e-x")
check("지금 실행: 없는 Job 404", s == 404, (s, b))
call("POST", f"{MON}/unscheduleJob/{cl}")
s, b = call("POST", f"{MON}/runScheduleJobNow/{cl}")
check("지금 실행: 예약되지 않은 Job 409", s == 409, (s, b))
call("POST", f"{MON}/rescheduleJob/{cl}")
hist = sql(f"select group_concat(detail) from config_change_hist where target_name='{cl}' and changed_at > now() - interval 1 minute")
check("지금 실행이 설정 변경 이력에 남는다", "run-now" in hist, hist)

# ── 감사는 지울 수 없다 ──
for method, path, body in (("DELETE", "/api/v1/ifsvc/audit/delete/999999999", None),
                           ("POST", "/api/v1/ifsvc/audit/delete", [999999999]),
                           ("POST", "/api/v1/ifsvc/audit/delete/timestamp", "2000-01-01T00:00:00.000+00:00")):
    s, _ = call(method, path, body)
    check(f"감사 삭제 API 없음: {method} {path} → 404·405", s in (404, 405), s)

print(f"\n{sum(results)}/{len(results)} passed")
sys.exit(0 if all(results) else 1)
