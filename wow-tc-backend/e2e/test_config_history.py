"""설정 변경 이력 e2e — 관리 API 로 바꾼 뒤 CONFIG_CHANGE_HIST 와 조회 API 를 확인한다."""
import json, os, sys, time, urllib.request, urllib.error
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import common  # noqa: E402

R = os.path.dirname(os.path.abspath(__file__))
BASE = common.BASE
TOKEN = common.admin_token()
RUN = str(int(time.time()))

def call(method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    req.add_header("Authorization", TOKEN)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
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
    print(("PASS " if cond else "FAIL ") + name + ("" if cond else f"  -> {str(detail)[:600]}"), flush=True)

def hist(t, tid):
    s, b = call("GET", f"/api/v1/config-history/{t}/{tid}")
    return b if s == 200 else []

# ── API 클라이언트·키 ──
cid = f"hist-{RUN}"
call("POST", "/api/v1/api-clients", {"clientId": cid, "name": "hist test", "allowedOpcodes": ["a.*"]})
call("PUT", f"/api/v1/api-clients/{cid}", {"name": "hist test 2", "allowedOpcodes": ["a.*", "b.*"]})
call("PUT", f"/api/v1/api-clients/{cid}", {"name": "hist test 2", "allowedOpcodes": ["a.*", "b.*"]})  # 변경 없음
call("POST", f"/api/v1/api-clients/{cid}/disable")
s, key = call("POST", f"/api/v1/api-clients/{cid}/keys", {})
call("POST", f"/api/v1/api-clients/{cid}/keys/{key['keyId']}/revoke")
call("DELETE", f"/api/v1/api-clients/{cid}")
h = hist("API_CLIENT", cid)
acts = [x["action"] for x in reversed(h)]
check("API 클라이언트: CREATE→UPDATE→DISABLE→DELETE (변경 없는 UPDATE 는 안 남음)", acts == ["CREATE", "UPDATE", "DISABLE", "DELETE"], acts)
upd = [x for x in h if x["action"] == "UPDATE"][0]
check("UPDATE 의 전·후 JSON", '"hist test"' in upd["beforeJson"] and '"hist test 2"' in upd["afterJson"], upd)
check("행위자 USER tc-tester, IP 127.0.0.1", all(x["actorType"] == "USER" and x["actorId"] == "tc-tester" and x["actorIp"] == "127.0.0.1" for x in h), h[:1])
kh = hist("API_KEY", key["keyId"])
txt = json.dumps(kh)
check("API 키: 발급·폐기 CONTROL 기록, 키 값·해시 없음", [x["detail"] for x in reversed(kh)] == ["key-issue", "key-revoke"]
      and "tck_" not in txt and "keyHash" not in txt, txt[:400])

# ── PLC 커넥터: 갱신·게시·해제 ──
s, c = call("GET", "/api/v1/plc-connector/find/byName/plc-fake")
pid = str(c["id"])
c2 = dict(c); c2["description"] = f"fake PLC {RUN}"
call("POST", "/api/v1/plc-connector/update", c2)
call("POST", "/api/v1/plc-connector/control/publish/byName/plc-fake")  # 가짜 PLC 가 꺼져 있어 실패
h = hist("PLC_CONNECTOR", pid)
recent = [x for x in h if x["changedAt"] >= h[0]["changedAt"] - 60_000] if h and isinstance(h[0]["changedAt"], int) else h[:3]
check("PLC 커넥터 UPDATE(설명 변경) 기록", any(x["action"] == "UPDATE" and RUN in (x["afterJson"] or "") for x in h), h[:2])
check("PLC 커넥터 게시 시도 CONTROL 기록(publish 또는 publish-failed)", any(x["action"] == "CONTROL" and (x["detail"] or "").startswith("publish") for x in h), [x.get("detail") for x in h[:4]])

# ── 인터페이스 사용 여부 일괄 ──
s, i1 = call("GET", "/api/v1/ifsvc/management/find/byName/plc.fake.read")
s, i2 = call("GET", "/api/v1/ifsvc/management/find/byName/plc.e2e.d100.read")
call("POST", "/api/v1/ifsvc/management/update/apply-use/byBatch/false", [i1["id"], i2["id"]])
call("POST", "/api/v1/ifsvc/management/update/apply-use/byBatch/true", [i1["id"], i2["id"]])
h1, h2 = hist("INTERFACE", str(i1["id"])), hist("INTERFACE", str(i2["id"]))
check("인터페이스 일괄 사용 중지·재개 → 각 인터페이스에 DISABLE·ENABLE", [x["action"] for x in h1[:2]] == ["ENABLE", "DISABLE"]
      and [x["action"] for x in h2[:2]] == ["ENABLE", "DISABLE"], (h1[:2], h2[:2]))
check("같은 요청의 일괄 변경은 TRANSACTION_ID 가 같음", h1[0]["transactionId"] == h2[0]["transactionId"] and h1[0]["transactionId"] != h1[1]["transactionId"],
      (h1[0]["transactionId"], h2[0]["transactionId"], h1[1]["transactionId"]))

# ── 전달 정의 ──
s, d = call("POST", "/api/v1/ifsvc/delivery", {"interfaceName": "plc.fake.read", "deliveryType": "CALLBACK_URL", "deliverOn": "ALL"})
did = d.get("id")
call("PUT", f"/api/v1/ifsvc/delivery/{did}", {"interfaceName": "plc.fake.read", "deliveryType": "CALLBACK_URL", "deliverOn": "SUCCESS"})
call("DELETE", f"/api/v1/ifsvc/delivery/{did}")
# 전달 정의 ID 는 {인터페이스}.{유형} 으로 정해져 실행마다 같다 — 최근 세 건만 본다
check("전달 정의 CREATE→UPDATE→DELETE", [x["action"] for x in reversed(hist("DELIVERY", did)[:3])] == ["CREATE", "UPDATE", "DELETE"], hist("DELIVERY", did)[:3])

# ── 스케줄 Job ──
jn = f"hist-job-{RUN}"
s, j = call("POST", "/api/v1/schedule-job/management/register", {"name": jn, "schedulerName": "TestScheduler", "use": False, "groupName": "E2E",
     "jobClassName": "com.wow.be.tc.schedule.jobs.PlcGeneralScheduleJob", "triggerType": "cron", "cronExpression": "0 0 0 1 1 ?",
     "startDelay": 0, "repeatInterval": 0, "jobDetails": [{"entryKey": "interfaceName", "entryValue": "plc.e2e.d100.read", "entryValueType": "string", "oper": "C"}]})
jid = j.get("id") if isinstance(j, dict) else None
call("DELETE", f"/api/v1/schedule-job/management/delete/byName/{jn}")
jh = hist("SCHEDULE_JOB", jid)
check("스케줄 Job CREATE→DELETE, 삭제 전 값에 상세(interfaceName) 포함", [x["action"] for x in reversed(jh)] == ["CREATE", "DELETE"]
      and "plc.e2e.d100.read" in (jh[0]["beforeJson"] or ""), jh)

# ── 검색 API ──
s, page = call("POST", "/api/v1/config-history/search", {"actorId": "tc-tester", "targetType": "API_CLIENT", "page": 0, "size": 5})
check("검색 API: 쪽 나눔·최신순", s == 200 and page["size"] == 5 and len(page["items"]) <= 5 and page["total"] >= 4, (s, page if s != 200 else page["total"]))

print(f"\n{sum(results)}/{len(results)} PASS")
sys.exit(0 if all(results) else 1)
