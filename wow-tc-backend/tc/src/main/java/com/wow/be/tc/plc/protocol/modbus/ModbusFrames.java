package com.wow.be.tc.plc.protocol.modbus;

import java.util.Map;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.plc.protocol.WireProtocol;

/**
 * Modbus TCP 프레임(MBAP + PDU) 조립과 해석 (docs/plc-protocol-design.md §6.2).
 *
 * <pre>
 * MBAP: 트랜잭션 ID(2) · 프로토콜 ID 0(2) · 길이(2, 단위 ID 부터 끝까지) · 단위 ID(1)
 * PDU : 함수 코드(1) · 데이터          — 예외 응답은 함수 코드 | 0x80 · 예외 코드(1)
 * </pre>
 * 모든 수는 빅엔디안. 트랜잭션 ID 는 연결(채널)이 정하므로 여기서는 0 으로 만든다 — {@link WireProtocol} 로 넣고 읽는다.
 * 요청마다 번호가 있어 시간 초과에도 연결을 끊지 않는다.
 */
public final class ModbusFrames implements WireProtocol {

	static final int MBAP = 7;

	/** MBAP 길이 칸의 상한 — 단위 ID(1) + PDU 최대 253 */
	static final int MAX_LENGTH = 254;

	final int unitId;

	public ModbusFrames(Map<String, Object> settings) {
		Object v = settings != null ? settings.get("unitId") : null;
		this.unitId = v instanceof Number n ? n.intValue() : v != null ? Integer.parseInt(v.toString()) : 1;
	}

	// ── 요청 ──

	/** 읽기(FC 1·2·3·4) */
	byte[] read(int function, int offset, int quantity) {
		return frame(new byte[] { (byte) function, (byte) (offset >> 8), (byte) offset, (byte) (quantity >> 8), (byte) quantity });
	}

	/** 레지스터 하나 쓰기(FC 6) — value 는 전선 순서 2바이트 */
	byte[] writeRegister(int offset, byte[] value) {
		return frame(new byte[] { 6, (byte) (offset >> 8), (byte) offset, value[0], value[1] });
	}

	/** 레지스터 여러 개 쓰기(FC 16) */
	byte[] writeRegisters(int offset, byte[] data) {
		int quantity = data.length / 2;
		byte[] pdu = new byte[6 + data.length];
		pdu[0] = 16;
		pdu[1] = (byte) (offset >> 8);
		pdu[2] = (byte) offset;
		pdu[3] = (byte) (quantity >> 8);
		pdu[4] = (byte) quantity;
		pdu[5] = (byte) data.length;
		System.arraycopy(data, 0, pdu, 6, data.length);
		return frame(pdu);
	}

	/** 코일 하나 쓰기(FC 5) — ON 은 FF00, OFF 는 0000 */
	byte[] writeCoil(int offset, boolean on) {
		return frame(new byte[] { 5, (byte) (offset >> 8), (byte) offset, (byte) (on ? 0xFF : 0x00), 0 });
	}

	/** 코일 여러 개 쓰기(FC 15) — 첫 코일이 첫 바이트의 가장 낮은 비트 */
	byte[] writeCoils(int offset, boolean[] bits) {
		int bytes = (bits.length + 7) / 8;
		byte[] pdu = new byte[6 + bytes];
		pdu[0] = 15;
		pdu[1] = (byte) (offset >> 8);
		pdu[2] = (byte) offset;
		pdu[3] = (byte) (bits.length >> 8);
		pdu[4] = (byte) bits.length;
		pdu[5] = (byte) bytes;
		for (int k = 0; k < bits.length; k++) {
			if (bits[k]) {
				pdu[6 + k / 8] |= (byte) (1 << (k % 8));
			}
		}
		return frame(pdu);
	}

	private byte[] frame(byte[] pdu) {
		byte[] out = new byte[MBAP + pdu.length];
		int len = 1 + pdu.length;
		out[4] = (byte) (len >> 8);
		out[5] = (byte) len;
		out[6] = (byte) unitId;
		System.arraycopy(pdu, 0, out, MBAP, pdu.length);
		return out;
	}

	// ── 응답 ──

	/**
	 * 응답 해석 — 프로토콜 ID, 길이 칸, 함수 코드(요청과 같거나 예외). 예외면 {@code exception} 이 0 이 아니다.
	 *
	 * @param function 요청의 함수 코드
	 */
	Reply reply(byte[] frame, int function) throws ToolControlException {
		if (frame.length < MBAP + 2) {
			throw bad("응답이 너무 짧습니다(" + frame.length + "바이트)");
		}
		if (frame[2] != 0 || frame[3] != 0) {
			throw bad("프로토콜 ID 가 0 이 아닙니다");
		}
		int len = (frame[4] & 0xFF) << 8 | (frame[5] & 0xFF);
		if (len != frame.length - 6) {
			throw bad("길이 칸(" + len + ")과 실제 길이(" + (frame.length - 6) + ")가 다릅니다");
		}
		int fc = frame[MBAP] & 0xFF;
		if (fc == (function | 0x80)) {
			return new Reply(frame[MBAP + 1] & 0xFF, null);
		}
		if (fc != function) {
			throw bad("함수 코드가 다릅니다(요청 " + function + ", 응답 " + fc + ")");
		}
		byte[] pdu = new byte[frame.length - MBAP - 1];
		System.arraycopy(frame, MBAP + 1, pdu, 0, pdu.length);
		return new Reply(0, pdu);
	}

	/** 읽기 응답의 데이터(바이트 수 칸 뒤) — 바이트 수가 맞는지 본다 */
	static byte[] readData(Reply reply, int expectedBytes) throws ToolControlException {
		byte[] pdu = reply.data();
		if (pdu.length < 1 || (pdu[0] & 0xFF) != expectedBytes || pdu.length != 1 + expectedBytes) {
			throw bad("읽은 데이터가 " + (pdu.length > 0 ? pdu[0] & 0xFF : 0) + "바이트 — " + expectedBytes + "바이트여야 합니다");
		}
		byte[] out = new byte[expectedBytes];
		System.arraycopy(pdu, 1, out, 0, expectedBytes);
		return out;
	}

	/** 쓰기 응답 — 시작 주소와 개수(FC 15·16) 또는 값(FC 5·6)을 그대로 돌려준다 */
	static void checkEcho(Reply reply, int offset, int second) throws ToolControlException {
		byte[] pdu = reply.data();
		if (pdu.length != 4 || ((pdu[0] & 0xFF) << 8 | (pdu[1] & 0xFF)) != offset || ((pdu[2] & 0xFF) << 8 | (pdu[3] & 0xFF)) != second) {
			throw bad("쓰기 응답이 요청과 맞지 않습니다");
		}
	}

	static ToolControlException bad(String why) {
		return ToolControlException.of(ToolControlException.PLC_ERROR, "Modbus 응답을 해석할 수 없습니다 — " + why);
	}

	/** @param exception 예외 코드(0 이면 정상) @param data 함수 코드 뒤의 PDU(예외면 null) */
	record Reply(int exception, byte[] data) {
	}

	// ── 전송 규약(WireProtocol) ──

	@Override
	public int responseLength(byte[] buf, int available) {
		if (available < 6) {
			return 0;
		}
		int len = (buf[4] & 0xFF) << 8 | (buf[5] & 0xFF);
		if (buf[2] != 0 || buf[3] != 0 || len < 2 || len > MAX_LENGTH) {
			return -1;
		}
		int total = 6 + len;
		return available < total ? 0 : total;
	}

	@Override
	public boolean hasSerial(byte[] request) {
		return true;
	}

	@Override
	public void setSerial(byte[] request, int serial) {
		request[0] = (byte) (serial >> 8);
		request[1] = (byte) serial;
	}

	@Override
	public int serialOf(byte[] response) {
		return response.length >= 2 ? (response[0] & 0xFF) << 8 | (response[1] & 0xFF) : -1;
	}

	/** 보유 레지스터 하나 읽기(프로토콜 주소 0). 장치에 그 주소가 없어도 예외 응답이 오면 살아 있다 */
	@Override
	public byte[] healthCheckRequest() {
		return read(3, 0, 1);
	}

	/** 같은 함수의 정상 응답이든 예외 응답이든 형식이 맞으면 살아 있다 */
	@Override
	public boolean healthCheckOk(byte[] response) {
		try {
			reply(response, 3);
			return true;
		} catch (ToolControlException | RuntimeException e) {
			return false;
		}
	}
}
