"""시험용 가짜 MELSEC PLC (MC 3E 바이너리, 워드 읽기만).

- n번째 요청에는 모든 워드 값을 n 으로 답한다 → 어느 요청의 응답인지 값으로 구별된다.
- DELAY 에 든 번호의 요청은 DELAY_SEC 초 늦게 답한다(늦은 응답 재현).
- CLOSE_AFTER 에 든 번호의 요청에 답한 뒤 연결을 끊는다(재연결 재현).
- 포트당 클라이언트 1개만 받는다(실제 PLC·vplc 처럼). 이미 있으면 새 연결을 바로 닫는다.
사용: python fake_plc.py PORT "DELAY=2,3" "CLOSE_AFTER=5" DELAY_SEC
"""
import socket, struct, sys, threading, time

PORT = int(sys.argv[1])
DELAY = {int(x) for x in sys.argv[2].split("=")[1].split(",") if x} if len(sys.argv) > 2 else set()
CLOSE_AFTER = {int(x) for x in sys.argv[3].split("=")[1].split(",") if x} if len(sys.argv) > 3 else set()
DELAY_SEC = float(sys.argv[4]) if len(sys.argv) > 4 else 3.0

lock = threading.Lock()
counter = [0]
active = [None]

def log(*a):
    print(time.strftime("%H:%M:%S"), *a, flush=True)

def recv_exact(conn, n):
    buf = b""
    while len(buf) < n:
        chunk = conn.recv(n - len(buf))
        if not chunk:
            raise ConnectionError("closed")
        buf += chunk
    return buf

def respond(conn, n, words, cid):
    data = struct.pack("<" + "H" * words, *([n & 0xFFFF] * words))
    body = b"\x00\x00" + data                      # end code + data
    frame = b"\xD0\x00\x00\xFF\xFF\x03\x00" + struct.pack("<H", len(body)) + body
    try:
        conn.sendall(frame)
        log(f"conn{cid} 응답 #{n} 보냄")
    except OSError as e:
        log(f"conn{cid} 응답 #{n} 못 보냄(연결 닫힘): {e}")

def serve(conn, cid):
    try:
        while True:
            head = recv_exact(conn, 9)              # subheader..reqDataLen
            length = struct.unpack("<H", head[7:9])[0]
            body = recv_exact(conn, length)
            words = struct.unpack("<H", body[-2:])[0]
            with lock:
                counter[0] += 1
                n = counter[0]
            log(f"conn{cid} 요청 #{n} ({words} words)")
            if n in DELAY:
                threading.Timer(DELAY_SEC, respond, (conn, n, words, cid)).start()
                continue
            respond(conn, n, words, cid)
            if n in CLOSE_AFTER:
                log(f"conn{cid} 요청 #{n} 뒤 연결을 끊음")
                conn.close()
                return
    except (ConnectionError, OSError) as e:
        log(f"conn{cid} 종료: {e}")
    finally:
        with lock:
            if active[0] is conn:
                active[0] = None
        try:
            conn.close()
        except OSError:
            pass

srv = socket.socket()
srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
srv.bind(("127.0.0.1", PORT))
srv.listen(8)
log(f"fake PLC listening :{PORT} DELAY={sorted(DELAY)} CLOSE_AFTER={sorted(CLOSE_AFTER)}")
cid = 0
while True:
    conn, addr = srv.accept()
    cid += 1
    with lock:
        busy = active[0] is not None
        if not busy:
            active[0] = conn
    if busy:
        log(f"conn{cid} 거부(슬롯 사용 중) {addr}")
        conn.close()
        continue
    log(f"conn{cid} 연결 {addr}")
    threading.Thread(target=serve, args=(conn, cid), daemon=True).start()
