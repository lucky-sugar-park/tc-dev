"""e2e 공통 설정. 환경 변수로 바꾸고, 없으면 로컬 개발 기본값을 쓴다. 비밀값은 출력하지 않는다.

TC_E2E_BASE_URL      tc 주소 (기본 http://localhost:8087)
TC_E2E_VPLC_URL      vplc 주소 (기본 http://localhost:8088)
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


def tc_pid():
    v = os.environ.get("TC_E2E_TC_PID", "")
    if os.path.isfile(v):
        return open(v, encoding="utf-8-sig").read().strip()
    return v


def tc_log():
    return os.environ.get("TC_E2E_TC_LOG", "")
