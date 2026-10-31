"""시험용 가짜 Modbus TCP 장치 (docs/plc-protocol-design.md §14).

- 함수 1·2·3·4(읽기), 5·6·15·16(쓰기). 영역마다 프로토콜 주소 0~999. 범위 밖은 예외 02, 모르는 함수는 예외 01.
- 처음 값: HR0=100, HR1..2=0x0001,0x0002(INT32 BIG 이면 65538), IR0..1="ABC", DI0..2=1,0,1. 나머지 0.
- HR500 을 읽으면 답하지 않는다(시간 초과 재현 — 연결은 그대로).
- HR990 에 n 을 쓰면 답한 뒤 n 초 동안 어떤 요청에도 답하지 않는다(쉬는 연결 확인 재현).
- 연결은 여럿 받는다(연결 시험이 게시 중에도 된다). 단위 ID 는 무엇이든 받아 그대로 돌려준다.
사용: python fake_modbus.py PORT
"""
import socket
import struct
import sys
import threading
import time

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 15200
SIZE = 1000
HANG = 500
MUTE = 990

hr = [0] * SIZE
ir = [0] * SIZE
co = [False] * SIZE
di = [False] * SIZE
hr[0], hr[1], hr[2] = 100, 0x0001, 0x0002
ir[0], ir[1] = 0x4142, 0x4300
di[0], di[1], di[2] = True, False, True
lock = threading.Lock()
mute_until = [0.0]


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


def pack_bits(bits):
    out = bytearray((len(bits) + 7) // 8)
    for k, b in enumerate(bits):
        if b:
            out[k // 8] |= 1 << (k % 8)
    return bytes(out)


def handle(pdu):
    """PDU → 응답 PDU(None 이면 답하지 않음)"""
    fc = pdu[0]
    exc = lambda code: bytes([fc | 0x80, code])
    if fc in (1, 2, 3, 4):
        start, qty = struct.unpack(">HH", pdu[1:5])
        if qty < 1 or start + qty > SIZE:
            return exc(2)
        if fc == 3 and start <= HANG < start + qty:
            return None
        with lock:
            if fc in (1, 2):
                data = pack_bits((co if fc == 1 else di)[start:start + qty])
            else:
                data = struct.pack(">" + "H" * qty, *(hr if fc == 3 else ir)[start:start + qty])
        return bytes([fc, len(data)]) + data
    if fc == 5:
        addr, value = struct.unpack(">HH", pdu[1:5])
        if addr >= SIZE or value not in (0xFF00, 0x0000):
            return exc(2 if addr >= SIZE else 3)
        with lock:
            co[addr] = value == 0xFF00
        return pdu[:5]
    if fc == 6:
        addr, value = struct.unpack(">HH", pdu[1:5])
        if addr >= SIZE:
            return exc(2)
        with lock:
            hr[addr] = value
        if addr == MUTE and value:
            mute_until[0] = time.time() + value
            log(f"{value}초 동안 답하지 않음")
        return pdu[:5]
    if fc in (15, 16):
        start, qty, nbytes = struct.unpack(">HHB", pdu[1:6])
        if qty < 1 or start + qty > SIZE:
            return exc(2)
        body = pdu[6:6 + nbytes]
        with lock:
            if fc == 15:
                for k in range(qty):
                    co[start + k] = bool(body[k // 8] >> (k % 8) & 1)
            else:
                for k, v in enumerate(struct.unpack(">" + "H" * qty, body)):
                    hr[start + k] = v
        return pdu[:5]
    return exc(1)


def serve(conn, cid):
    try:
        while True:
            head = recv_exact(conn, 7)
            tid, pid, length, unit = struct.unpack(">HHHB", head)
            pdu = recv_exact(conn, length - 1)
            if time.time() < mute_until[0]:
                log(f"conn{cid} tid={tid} 무시(답하지 않는 중)")
                continue
            res = handle(pdu)
            if res is None:
                log(f"conn{cid} tid={tid} fc={pdu[0]} 답하지 않음(HR{HANG})")
                continue
            conn.sendall(struct.pack(">HHHB", tid, 0, len(res) + 1, unit) + res)
    except (ConnectionError, OSError):
        pass
    finally:
        conn.close()
        log(f"conn{cid} 닫힘")


def main():
    srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("127.0.0.1", PORT))
    srv.listen(8)
    log(f"가짜 Modbus 장치 :{PORT}")
    cid = 0
    while True:
        conn, addr = srv.accept()
        cid += 1
        log(f"conn{cid} 연결 {addr}")
        threading.Thread(target=serve, args=(conn, cid), daemon=True).start()


if __name__ == "__main__":
    main()
