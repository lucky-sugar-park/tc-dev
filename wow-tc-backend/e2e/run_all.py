"""e2e 묶음을 차례로 돌린다. 가짜 PLC(:15100)는 여기서 띄우고 끝나면 내린다.

  python -X utf8 run_all.py              # 정상 종료 시험(test_shutdown)은 빼고
  python -X utf8 run_all.py --shutdown   # 마지막에 tc 를 정상 종료시키는 시험까지

tc 를 어떻게 띄워야 하는지는 README.md 참고.
"""
import os
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
SUITES = ["setup_e2e.py", "test_plc_adapter.py main", "test_api_key.py", "test_paths.py", "test_config_history.py", "test_users.py"]


def start_fake_plc(*args):
    log = open(os.path.join(HERE, "fake.out.log"), "w", encoding="utf-8")
    p = subprocess.Popen([sys.executable, "-u", os.path.join(HERE, "fake_plc.py"), "15100", *args], stdout=log, stderr=subprocess.STDOUT)
    time.sleep(1)
    return p


def run(suite):
    print(f"=== {suite}", flush=True)
    parts = suite.split()
    r = subprocess.run([sys.executable, "-X", "utf8", os.path.join(HERE, parts[0]), *parts[1:]],
                       env=dict(os.environ, EXPIRY_CRON_FAST=os.environ.get("EXPIRY_CRON_FAST", "1")))
    return r.returncode == 0


fake = start_fake_plc("DELAY=2", "CLOSE_AFTER=5", "5")
failed = []
try:
    for s in SUITES:
        if not run(s):
            failed.append(s)
finally:
    fake.terminate()

if "--shutdown" in sys.argv:
    # 지연 2초 — 종료 요청(1초 뒤)보다 길고, 시험 설정의 PLC 시간 초과(3초)보다 짧게
    fake = start_fake_plc("DELAY=1", "CLOSE_AFTER=", "2")
    try:
        if not run("test_shutdown.py"):
            failed.append("test_shutdown.py")
    finally:
        fake.terminate()

print("\n실패:", failed if failed else "없음")
sys.exit(1 if failed else 0)
