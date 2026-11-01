package com.wow.be.tc.vplc.melsec;

import java.nio.charset.StandardCharsets;

import com.wow.be.tc.vplc.melsec.MelsecTypes.MemoryType;
import com.wow.be.tc.vplc.VirtualPlcException;
import com.wow.be.tc.vplc.melsec.transport.McCodec;
import com.wow.be.tc.vplc.melsec.transport.McCodec.McRequest;

/**
 * 가상 PLC 의 MC 요청 처리 — 워드·비트 일괄 읽기(0401)·쓰기(1401), 루프백(0619). 응답 프레임(바이트)을 돌려준다.
 *
 * <ul>
 *   <li>만들지 않은 메모리 종류나 범위 밖이면 종료 코드 {@link #ERR_DEVICE}(0xC056), 모르는 명령은 {@link #ERR_COMMAND}(0xC059)로
 *       답한다 — 실제 PLC 처럼. 예전에는 답하지 않아 tc 가 시간 초과로만 알았다.</li>
 *   <li>비트 단위(서브 명령 0001): 비트 디바이스만. 메모리는 워드라 비트 번호 n 을 워드 n/16 의 n%16 번째 비트로 본다.
 *       워드 디바이스를 비트 단위로 부르면 {@link #ERR_UNIT}(0xC05B).</li>
 * </ul>
 */
public final class McResponder {

	public static final int ERR_DEVICE = 0xC056;

	public static final int ERR_COMMAND = 0xC059;

	/** 워드 디바이스를 비트 단위(서브 명령 0001)로 읽고 쓰려 함 */
	public static final int ERR_UNIT = 0xC05B;

	private McResponder() {
	}

	public static byte[] handle(McRequest r, MelsecVirtualPlc vplc) {
		try {
			return switch (r.command()) {
				case McCodec.CMD_LOOPBACK -> McCodec.response(r, 0, r.body());
				case McCodec.CMD_READ, McCodec.CMD_WRITE -> readWrite(r, vplc);
				default -> McCodec.response(r, ERR_COMMAND, null);
			};
		} catch (RuntimeException | VirtualPlcException e) {
			return McCodec.response(r, ERR_COMMAND, null);
		}
	}

	private static byte[] readWrite(McRequest r, MelsecVirtualPlc vplc) throws VirtualPlcException {
		MemoryType type = r.ascii() ? MemoryType.toMemoryType(r.deviceAscii()) : MemoryType.toMemoryType(r.deviceCode());
		Memory mem = type != null ? vplc.memory(type) : null;
		if (mem == null) {
			return McCodec.response(r, ERR_DEVICE, null);
		}
		boolean bits = r.subCommand() == 0x0001;
		if (bits && !type.isBitDevice()) {
			return McCodec.response(r, ERR_UNIT, null);  // 실제 PLC 처럼 — 예전 vplc 는 서브 명령을 보지 않아 이런 정의도 통과했다
		}
		int words = mem.getWords().length;
		int lastWord = bits ? (r.start() + r.count() - 1) / 16 : r.start() + r.count() - 1;
		if (r.count() < 1 || r.start() < 0 || lastWord >= words) {
			return McCodec.response(r, ERR_DEVICE, null);
		}
		if (r.command() == McCodec.CMD_READ) {
			return McCodec.response(r, 0, bits ? readBits(r, mem) : readWords(r, vplc, type));
		}
		if (bits) {
			writeBits(r, mem);
		} else {
			vplc.write(r.start(), r.count(), wordBytes(r), type);
		}
		return McCodec.response(r, 0, new byte[0]);
	}

	private static byte[] readWords(McRequest r, MelsecVirtualPlc vplc, MemoryType type) throws VirtualPlcException {
		byte[] le = vplc.read(r.start(), r.count(), type);
		if (!r.ascii()) {
			return le;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < le.length; i += 2) {
			sb.append(String.format("%04X", (le[i] & 0xFF) | (le[i + 1] & 0xFF) << 8));
		}
		return sb.toString().getBytes(StandardCharsets.US_ASCII);
	}

	/** 쓰기 데이터 → 리틀엔디안 워드 바이트(ASCII 는 워드마다 16진 4자리) */
	private static byte[] wordBytes(McRequest r) {
		if (!r.ascii()) {
			return r.body();
		}
		String t = new String(r.body(), StandardCharsets.US_ASCII);
		byte[] out = new byte[r.count() * 2];
		for (int i = 0; i < r.count(); i++) {
			int w = Integer.parseInt(t.substring(i * 4, i * 4 + 4), 16);
			out[i * 2] = (byte) w;
			out[i * 2 + 1] = (byte) (w >> 8);
		}
		return out;
	}

	private static boolean bit(Memory mem, int n) {
		return ((mem.getWords()[n / 16] >> (n % 16)) & 1) == 1;
	}

	private static byte[] readBits(McRequest r, Memory mem) {
		if (r.ascii()) {
			StringBuilder sb = new StringBuilder();
			for (int k = 0; k < r.count(); k++) {
				sb.append(bit(mem, r.start() + k) ? '1' : '0');
			}
			return sb.toString().getBytes(StandardCharsets.US_ASCII);
		}
		byte[] out = new byte[(r.count() + 1) / 2];
		for (int k = 0; k < r.count(); k++) {
			if (bit(mem, r.start() + k)) {
				out[k / 2] |= (byte) (k % 2 == 0 ? 0x10 : 0x01);
			}
		}
		return out;
	}

	private static void writeBits(McRequest r, Memory mem) {
		short[] words = mem.getWords();
		for (int k = 0; k < r.count(); k++) {
			boolean on = r.ascii() ? r.body()[k] == '1' : ((r.body()[k / 2] >> (k % 2 == 0 ? 4 : 0)) & 0x0F) == 1;
			int n = r.start() + k;
			int mask = 1 << (n % 16);
			words[n / 16] = (short) (on ? (words[n / 16] | mask) : (words[n / 16] & ~mask));
		}
	}
}
