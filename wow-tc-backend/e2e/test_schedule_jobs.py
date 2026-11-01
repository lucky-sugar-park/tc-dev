"""스케줄 Job e2e (docs/schedule-jobs.md) — 종류·저장 검사, 실행 이력의 성공·실패 판정과 비고, 목록의 최근 실행·다음 실행,
이력 검색, 속성 통째로 바꾸기, WRITE Job 이 PLC 에 쓰는 값, 정리 Job, 멈춤·재개·삭제.
전제: setup_e2e(plc-e2e·plc.e2e.d100.read), vplc plc-e2e 게시됨. 약 40초 걸린다."""
import json, os, re, subprocess, sys, time, urllib.request, urllib.error
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402

BASE = common.BASE
TOKEN = common.admin_token()
RUN = str(int(time.time()))
M = "/api/v1/schedule-job/management"
MON = "/api/v1/schedule-job/monitoring"
PLC_JOB = "com.wow.be.tc.schedule.jobs.PlcGeneralScheduleJob"
CLEANUP_JOB = "com.wow.be.tc.schedule.jobs.DataTableFlashJob"


def sql(q):
    pw = [re.match(r"\s*TC_DB_PASSWORD\s*:\s*(.*)", l).group(1).strip().strip('"').strip("'")
          for l in open(common.LOCAL_YML, encoding="utf-8") if re.match(r"\s*TC_DB_PASSWORD\s*:", l)][0]
    out = subprocess.run([common.MYSQL, "-uroot", "-N", "-B", "tc", "-e", q], env=dict(os.environ, MYSQL_PWD=pw),
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


def job(name, cls, props, interval=2000, use=True):
    return {"name": name, "use": use, "groupName": "E2E", "jobClassName": cls, "triggerType": "simple",
            "startDelay": 500, "repeatInterval": interval, "description": "e2e", "jobDetails": props}


def listed(name):
    return next((j for j in call("POST", f"{M}/search", {"conditions": []})[1] if j["name"] == name), None)


def hist(name, **q):
    return call("POST", f"{M}/exec-histories/query", {"jobName": name, "page": 0, "size": 50, **q})


def read_d100():
    s, b = call("POST", "/api/v1/ifsvc/request-and-reply", {"transactionId": f"t-sj-{RUN}-{time.time_ns()}", "messageId": "m",
                "opcode": "plc.e2e.d100.read", "sender": "e2e", "receiver": "tc", "data": {}})
    return b.get("data", {}) if s == 200 and isinstance(b, dict) else {}


created = []


def register(j):
    s, b = call("POST", f"{M}/register", j)
    if s == 200:
        created.append(j["name"])
        call("POST", f"{MON}/rescheduleJob/{j['name']}")
    return s, b


# ── 준비: WRITE 인터페이스, 곧 지울 READ 인터페이스(실패 경로) ──
for name, kind in (("plc.e2e.d100.write", "WRITE"), (f"plc.e2e.sj.tmp.{RUN}", "READ")):
    s, b = call("GET", f"/api/v1/ifsvc/management/find/byName/{name}")
    if s != 200 or not isinstance(b, dict) or not b.get("name"):
        call("POST", "/api/v1/ifsvc/management/register", common.d100_interface(name, kind, description="e2e schedule"))
TMP_IF = f"plc.e2e.sj.tmp.{RUN}"

# ── 1. 종류 ──
s, kinds = call("GET", f"{M}/job-kinds")
names = {k["kind"]: k for k in kinds} if s == 200 else {}
check("Job 종류 PLC·CLEANUP, 클래스·속성 정의", set(names) == {"PLC", "CLEANUP"} and names["PLC"]["jobClassName"] == PLC_JOB
      and any(p["key"] == "interfaceName" and p["required"] for p in names["PLC"]["properties"])
      and "SCHEDULE_JOB_EXEC_HIST" in next(p for p in names["CLEANUP"]["properties"] if p["key"] == "cleanupTarget")["options"], kinds)

# ── 2. 저장 검사(400) ──
cases = [
    ("등록할 수 없는 클래스", job(f"e2e-sj-x1-{RUN}", "java.lang.Thread", [prop("interfaceName", "plc.e2e.d100.read")]), "Job 종류"),
    ("없는 인터페이스", job(f"e2e-sj-x2-{RUN}", PLC_JOB, [prop("interfaceName", "no.such.interface")]), "인터페이스가 없습니다"),
    ("READ 에 속성 더함", job(f"e2e-sj-x3-{RUN}", PLC_JOB, [prop("interfaceName", "plc.e2e.d100.read"), prop("d100", "1", "int")]), "READ"),
    ("WRITE 점 값 빠짐", job(f"e2e-sj-x4-{RUN}", PLC_JOB, [prop("interfaceName", "plc.e2e.d100.write"), prop("d100", "1", "int")]), "d101"),
    ("int 아닌 값", job(f"e2e-sj-x5-{RUN}", PLC_JOB, [prop("interfaceName", "plc.e2e.d100.write"), prop("d100", "x", "int"), prop("d101", "1", "int")]), "int"),
    ("ref 값 형", job(f"e2e-sj-x6-{RUN}", PLC_JOB, [prop("interfaceName", "plc.e2e.d100.read", "ref")]), "string·int·long"),
    ("정리 Job 표", job(f"e2e-sj-x7-{RUN}", CLEANUP_JOB, [prop("cleanupTarget", "USERS"), prop("savingDays", "7", "int")]), "정리할 수 없는"),
    ("간격 1000ms 미만", job(f"e2e-sj-x8-{RUN}", PLC_JOB, [prop("interfaceName", "plc.e2e.d100.read")], interval=100), "1000ms"),
]
for label, j, part in cases:
    s, b = call("POST", f"{M}/register", j)
    check(f"저장 검사 400 — {label}", s == 400 and part in (b.get("message", "") if isinstance(b, dict) else str(b)), (s, b))

# ── 3. 실행 — 성공·실패·WRITE·정리 ──
ok, bad, wr, cl = (f"e2e-sj-{x}-{RUN}" for x in ("ok", "bad", "wr", "cl"))
v1 = int(RUN) % 30000
for j in (job(ok, PLC_JOB, [prop("interfaceName", "plc.e2e.d100.read")]),
          job(bad, PLC_JOB, [prop("interfaceName", TMP_IF)]),
          job(wr, PLC_JOB, [prop("interfaceName", "plc.e2e.d100.write"), prop("d100", str(v1), "int"), prop("d101", "-77", "int")], interval=600000),
          job(cl, CLEANUP_JOB, [prop("cleanupTarget", "SCHEDULE_JOB_EXEC_HIST"), prop("savingDays", "3650", "int")], interval=600000)):
    s, b = register(j)
    check(f"등록 200: {j['name'][:12]}", s == 200, (s, b))
s, b = register(job(ok, PLC_JOB, [prop("interfaceName", "plc.e2e.d100.read")]))
check("같은 이름 등록 → 409", s == 409, (s, b))
# 등록한 뒤 인터페이스를 지우면 실행 때 실패한다
s, i = call("GET", f"/api/v1/ifsvc/management/find/byName/{TMP_IF}")
if s == 200 and isinstance(i, dict) and i.get("id"):
    call("DELETE", f"/api/v1/ifsvc/management/delete/{i['id']}")
time.sleep(8)

s, p = hist(ok)
items = p.get("items", []) if s == 200 else []
check("PLC Job 성공 이력 — SUCCESS, 비고에 거래 ID·값 수, 실행 서버·걸린 시간", items and all(x["executionResult"] == "SUCCESS" for x in items)
      and re.search(r"\(trx \S+\) — 값 2개", items[0]["remark"] or "") and items[0].get("instanceId") and items[0].get("elapsedMs") is not None, items[:1])
trx = re.search(r"\(trx (\S+)\)", items[0]["remark"]).group(1) if items else ""
check("비고의 거래 ID 로 송수신 기록이 있다", trx and sql(f"select count(*) from EXCHANGED_MESSAGE_AUDIT where TRX_ID='{trx}'") not in ("", "0"), trx)
check("이력 최신순", len(items) < 2 or items[0]["seqNo"] > items[1]["seqNo"], [x["seqNo"] for x in items])

s, p = hist(bad)
items = p.get("items", []) if s == 200 else []
check("지워진 인터페이스 Job → FAIL, 비고에 오류 코드와 인터페이스", items and all(x["executionResult"] == "FAIL" for x in items)
      and "ERR_" in items[0]["remark"] and TMP_IF in items[0]["remark"], items[:1])
s, p = hist(bad, result="SUCCESS")
check("이력 결과 거르기(SUCCESS) → 0건", s == 200 and p["total"] == 0, p)
s, p = call("POST", f"{M}/exec-histories/query", {"jobName": ok, "page": 0, "size": 1})
check("이력 쪽 크기 1·전체 건수", s == 200 and len(p["items"]) == 1 and p["total"] >= 2, p)
future = "2099-01-01T00:00:00Z"
s, p = hist(ok, **{"from": future})
check("이력 기간 거르기 → 0건", s == 200 and p["total"] == 0, p)

d = read_d100()
check(f"WRITE Job 이 속성 값을 PLC 에 썼다(d100={v1}, d101=-77)", d.get("d100") == v1 and d.get("d101") == -77, d)
s, p = hist(cl)
items = p.get("items", []) if s == 200 else []
check("정리 Job 성공 — 비고에 표·지운 건수", items and items[0]["executionResult"] == "SUCCESS"
      and re.search(r"SCHEDULE_JOB_EXEC_HIST: 3650일 지난 \d+건 삭제", items[0]["remark"] or ""), items[:1])

# ── 4. 목록의 최근 실행·다음 실행·최근 실패 ──
o, b2 = listed(ok), listed(bad)
check("목록: 종류·최근 결과 SUCCESS·다음 실행·실패 0", o and o.get("kind") == "PLC" and o.get("lastResult") == "SUCCESS"
      and o.get("nextFireTime") and o.get("lastExecutionTime") and o.get("recentFailCount") == 0, o)
check("목록: 실패 Job 은 최근 결과 FAIL·최근 실패 수 ≥ 1·비고", b2 and b2.get("lastResult") == "FAIL" and (b2.get("recentFailCount") or 0) >= 1
      and TMP_IF in (b2.get("lastRemark") or ""), b2)
check("실행해도 SCHED_NAME 을 덮어쓰지 않는다(예전: NO_ASSIGNED)", o and o.get("schedulerName") not in (None, "", "NO_ASSIGNED"), o and o.get("schedulerName"))

# ── 5. 속성 통째로 바꾸기 ──
w = listed(wr)
w["jobDetails"] = [prop("interfaceName", "plc.e2e.d100.write"), prop("d100", "5", "int"), prop("d101", "6", "int")]
s, b = call("POST", f"{M}/update", w)
check("WRITE Job 속성 수정 200", s == 200, (s, b))
r = listed(cl)
r["jobDetails"] = [prop("cleanupTarget", "SCHEDULE_JOB_EXEC_HIST"), prop("savingDays", "3650", "int"), prop("extra", "1")]
s, b = call("POST", f"{M}/update", r)
check("정리 Job 에 다른 속성 → 400", s == 400, (s, b))
r["jobDetails"] = [prop("cleanupTarget", "PLC_CONNECTION_HIST"), prop("savingDays", "3650", "int")]
r["jobClassName"] = PLC_JOB
s, b = call("POST", f"{M}/update", r)
check("종류 바꾸기 → 400", s == 400, (s, b))
# 빠진 키는 지워진다 — READ 로 바꾸면 d100·d101 이 없어져야 한다
w["jobDetails"] = [prop("interfaceName", "plc.e2e.d100.read")]
s, b = call("POST", f"{M}/update", w)
s2, ds = call("GET", f"{M}/search/job-details/byName/{wr}")
check("수정에서 빠진 속성은 지워진다(예전: 남아서 계속 쓰였다)", s == 200 and sorted(x["entryKey"] for x in ds) == ["interfaceName"], (s, b, ds))

# ── 6. 멈춤·재개·삭제 ──
s, b = call("POST", f"{MON}/pauseScheduleJob/{ok}")
check("멈춤 → PAUSED", s == 200 and listed(ok)["triggerState"] == "PAUSED", (s, listed(ok)))
s, b = call("POST", f"{MON}/resumeScheduleJob/{ok}")
check("재개 → NORMAL", s == 200 and listed(ok)["triggerState"] == "NORMAL", (s, listed(ok)))
for n in created:
    call("DELETE", f"{M}/delete/byName/{n}")
before = sql(f"select count(*) from SCHEDULE_JOB_EXEC_HIST where JOB_NAME='{ok}'")
time.sleep(4)
after = sql(f"select count(*) from SCHEDULE_JOB_EXEC_HIST where JOB_NAME='{ok}'")
q = sql(f"select count(*) from QRTZ_JOB_DETAILS where JOB_NAME like 'e2e-sj-%-{RUN}'")
check("삭제한 Job 은 더 실행되지 않고 Quartz 에서도 내려감", before == after and q == "0", (before, after, q))

print(f"\n{sum(results)}/{len(results)} passed")
sys.exit(0 if all(results) else 1)
