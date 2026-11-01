"""e2e 공통 설정. 환경 변수로 바꾸고, 없으면 로컬 개발 기본값을 쓴다. 비밀값은 출력하지 않는다.

TC_E2E_BASE_URL      tc 주소 (기본 http://localhost:8087)
TC_E2E_VPLC_URL      vplc 주소 (기본 http://localhost:8088) — MELSEC·Modbus·OPC UA 가상 PLC 를 모두 띄운다
TC_E2E_ADMIN_ID      관리자 아이디 (기본 tc-tester)
TC_E2E_ADMIN_PASSWORD  관리자 비밀번호 — 없으면 TC_E2E_ADMIN_FILE(id=…/password=… 두 줄)에서 읽는다
TC_E2E_LOCAL_YML     DB 비밀번호(TC_DB_PASSWORD)를 읽을 application-local.yml (기본 tc/src/main/resources/application-local.yml)
TC_E2E_MYSQL         mysql 클라이언트 경로
TC_E2E_REDIS_CLI     redis-cli(또는 memurai-cli) 경로
TC_E2E_TC_PID        정상 종료 시험용 — tc 프로세스 ID 또는 그 값이 든 파일
TC_E2E_TC_LOG        정상 종료 시험용 — tc 표준 출력 로그 파일
"""
import json
import os
import urllib.error
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)

BASE = os.environ.get("TC_E2E_BASE_URL", "http://localhost:8087")
VPLC_BASE = os.environ.get("TC_E2E_VPLC_URL", "http://localhost:8088")
LOCAL_YML = os.environ.get("TC_E2E_LOCAL_YML", os.path.join(REPO, "tc", "src", "main", "resources", "application-local.yml"))
MYSQL = os.environ.get("TC_E2E_MYSQL", r"C:\Program Files\MariaDB 11.4\bin\mysql.exe")
REDIS_CLI = os.environ.get("TC_E2E_REDIS_CLI", r"C:\Program Files\Memurai\memurai-cli.exe")

_token = None


def _admin_credentials():
    uid = os.environ.get("TC_E2E_ADMIN_ID", "tc-tester")
    pw = os.environ.get("TC_E2E_ADMIN_PASSWORD")
    if not pw and os.environ.get("TC_E2E_ADMIN_FILE"):
        for line in open(os.environ["TC_E2E_ADMIN_FILE"], encoding="utf-8"):
            k, _, v = line.strip().partition("=")
            if k == "id":
                uid = v
            elif k == "password":
                pw = v
    if not pw:
        raise SystemExit("관리자 비밀번호가 없습니다 — TC_E2E_ADMIN_PASSWORD 나 TC_E2E_ADMIN_FILE 을 설정하세요")
    return uid, pw


def admin_token():
    """관리자로 로그인해 받은 'Bearer …' 헤더 값(한 번만 로그인)."""
    global _token
    if _token is None:
        uid, pw = _admin_credentials()
        req = urllib.request.Request(BASE + "/api/auth/login", data=json.dumps({"id": uid, "password": pw}).encode(), method="POST")
        req.add_header("Content-Type", "application/json")
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                _token = r.headers.get("Authorization")
        except urllib.error.HTTPError as e:
            raise SystemExit(f"관리자 로그인 실패: HTTP {e.code}")
    return _token


def d100_interface(name, interface_type="READ", plc_name="plc-e2e", description="e2e D100..D101"):
    """D100·D101(INT16) 두 점 인터페이스 정의 — setup_e2e·test_paths·test_plc_adapter 가 같이 쓴다."""
    return {"name": name, "plcName": plc_name, "interfaceType": interface_type,
            "points": [{"name": "d100", "address": "D100", "type": "INT16", "description": "D100"},
                       {"name": "d101", "address": "D101", "type": "INT16", "description": "D101"}],
            "use": True, "description": description}


def tc_pid():
    v = os.environ.get("TC_E2E_TC_PID", "")
    if os.path.isfile(v):
        return open(v, encoding="utf-8-sig").read().strip()
    return v


def tc_log():
    return os.environ.get("TC_E2E_TC_LOG", "")


# ---- vplc 의 Modbus·OPC UA 가상 PLC(docs/virtual-devices.md) ----

VPLC_OPCUA_PASSWORD = os.environ.get("VPLC_OPCUA_PASSWORD", "tc-e2e-pass")

# 게이트웨이 한 포트(15020) 뒤 단위 ID 1·2 — 커넥터 plc-modbus-vplc·plc-vmodbus-e2e 가 붙는다
E2E_MODBUS = [
    {"name": "mb-e2e", "protocol": "MODBUS_TCP", "ipAddress": "127.0.0.1", "startPort": 15020, "portConnLimit": 8,
     "settings": {"unitId": 1, "size": 10000, "delayMs": 0}, "description": "tc e2e Modbus 단위 1"},
    {"name": "mb-e2e-2", "protocol": "MODBUS_TCP", "ipAddress": "127.0.0.1", "startPort": 15020, "portConnLimit": 8,
     "settings": {"unitId": 2, "size": 1000, "delayMs": 0}, "description": "tc e2e Modbus 단위 2(게이트웨이 뒤)"},
]

# opc.tcp://127.0.0.1:4840/vplc — 커넥터 plc-opcua-vplc·plc-opcua-e2e 가 붙는다
E2E_OPCUA = {"name": "ua-e2e", "protocol": "OPC_UA", "ipAddress": "127.0.0.1", "startPort": 4840,
             "settings": {"path": "/vplc", "requireLogin": False, "trustAllClients": True}, "description": "tc e2e OPC UA 서버"}

E2E_OPCUA_TAGS = [
    {"path": "Line1.Temp", "type": "FLOAT64", "value": 25.5, "description": "온도"},
    {"path": "Line1.Count", "type": "INT32", "value": 0, "description": "생산 수량"},
    {"path": "Line1.Speed", "type": "UINT16", "value": 1500, "description": "속도"},
    {"path": "Line1.Running", "type": "BOOL", "value": True, "description": "운전 중"},
    {"path": "Line1.Recipe", "type": "STRING", "value": "R-01", "description": "레시피"},
    {"path": "Line1.Raw", "type": "INT16", "value": 253, "description": "원시 값(배율 0.1 시험)"},
    {"path": "Line1.Counter", "type": "INT32", "value": 0, "simulation": {"kind": "RAMP", "periodMs": 1000, "min": 0, "max": 1000},
     "description": "1초마다 1씩"},
    {"path": "Line1.Wave", "type": "FLOAT64", "value": 0, "simulation": {"kind": "SINE", "periodMs": 500, "min": 0, "max": 100},
     "description": "사인파"},
    {"path": "Line1.Blink", "type": "BOOL", "value": False, "simulation": {"kind": "TOGGLE", "periodMs": 2000}, "description": "2초마다 켜고 끄기"},
    {"path": "Line2.Temp", "type": "FLOAT32", "value": 18.5, "description": "2라인 온도"},
]


def vplc(method, path, body=None):
    """vplc REST — (상태, 본문). vplc 가 없으면 (0, None)."""
    req = urllib.request.Request(VPLC_BASE + path, data=json.dumps(body).encode() if body is not None else None, method=method)
    req.add_header("Content-Type", "application/json")
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
    except urllib.error.URLError:
        return 0, None


def ensure_vplc(spec, tags=None):
    """가상 PLC 가 없으면 등록(태그 포함)하고, 실행 중이 되게 게시·시작·재개한다. 그 정보(아이디 포함) — vplc 가 없으면 None."""
    s, info = vplc("GET", "/api/v1/vplc/management/find/byName/" + spec["name"])
    if s == 0:
        return None
    if s == 404:
        s, _ = vplc("POST", "/api/v1/vplc/management/register", dict(spec, published=False, memoryTypeList=[]))
        if s != 200:
            raise SystemExit(f"가상 PLC 등록 실패: {spec['name']} HTTP {s}")
        s, info = vplc("GET", "/api/v1/vplc/management/find/byName/" + spec["name"])
        for t in tags or []:
            vplc("POST", f"/api/v1/vplc/opcua/{info['id']}/tags", t)
    vid = info["id"]
    if info["status"] in ("REGISTERED", "RELEASED"):
        vplc("POST", "/api/v1/vplc/management/publish/" + vid)
        info["status"] = "PUBLISHED"
    if info["status"] in ("PUBLISHED", "STOPPED"):
        vplc("POST", "/api/v1/vplc/control/start/" + vid)
    elif info["status"] == "PAUSED":
        vplc("POST", "/api/v1/vplc/control/resume/" + vid)
    s, info = vplc("GET", "/api/v1/vplc/management/find/" + vid)
    return info
