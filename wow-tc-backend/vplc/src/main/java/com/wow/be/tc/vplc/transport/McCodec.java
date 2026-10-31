package com.wow.be.tc.vplc.transport;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * MC 프로토콜 3E·4E 요청 해석과 응답 조립 (BINARY·ASCII) — 가상 PLC 쪽.
 *
 * <ul>
 *   <li>4E 는 서브헤더 뒤에 일련번호(2바이트)·예비(2바이트)가 붙고, 응답은 그 일련번호를 그대로 돌려준다.</li>
 *   <li>ASCII 의 디바이스 번호는 디바이스에 따라 10진 또는 16진 6자리다(X·Y·B·W·SB·SW·DX·DY 가 16진).</li>
 *   <li>오류 응답(종료 코드 ≠ 0)은 데이터 자리에 오류 정보(요청의 네트워크·PC·IO·국 번호와 명령·서브 명령)를 담는다.</li>
 * </ul>
 */
public final class McCodec {

	public static final int CMD_READ = 0x0401;

	public static final int CMD_WRITE = 0x1401;

	public static final int CMD_LOOPBACK = 0x0619;

	private static final Set<String> HEX_DEVICES = Set.of("X*", "Y*", "B*", "W*", "SB", "SW", "DX", "DY");

	private McCodec() {
	}

	/** 요청 하나 — 디바이스 지정 명령이면 device·start·count, 나머지(쓰기 데이터·루프백 데이터)는 body(프레임 형식 그대로) */
	public record McRequest(boolean ascii, boolean fourE, int serial, int network, int pc, int io, int station,
			int command, int subCommand, byte deviceCode, String deviceAscii, int start, int count, byte[] body) {
	}

	/** 받은 바이트 앞의 요청 프레임 길이. 덜 왔으면 0, 요청 머리가 아니면 -1 */
	public static int requestLength(byte[] buf, int available, boolean ascii) {
		if (ascii) {
			if (available < 4) {
				return 0;
			}
			String sub = new String(buf, 0, 4, StandardCharsets.US_ASCII);
			int at = "5000".equals(sub) ? 14 : "5400".equals(sub) ? 22 : -1;
			if (at < 0) {
				return -1;
			}
			if (available < at + 4) {
				return 0;
			}
			try {
				int total = at + 4 + Integer.parseInt(new String(buf, at, 4, StandardCharsets.US_ASCII), 16);
				return available < total ? 0 : total;
			} catch (NumberFormatException e) {
				return -1;
			}
		}
		if (available < 2) {
			return 0;
		}
		int at = buf[0] == 0x50 && buf[1] == 0x00 ? 7 : buf[0] == 0x54 && buf[1] == 0x00 ? 11 : -1;
		if (at < 0) {
			return -1;
		}
		if (available < at + 2) {
			return 0;
		}
		int total = at + 2 + ((buf[at] & 0xFF) | (buf[at + 1] & 0xFF) << 8);
		return available < total ? 0 : total;
	}

	public static McRequest parse(byte[] f, boolean ascii) {
		if (ascii) {
			String t = new String(f, StandardCharsets.US_ASCII);
			boolean fourE = t.startsWith("5400");
			int serial = fourE ? hex(t, 4, 4) : -1;
			int i = fourE ? 12 : 4;
			int network = hex(t, i, 2);
			int pc = hex(t, i + 2, 2);
			int io = hex(t, i + 4, 4);
			int station = hex(t, i + 8, 2);
			int command = hex(t, i + 18, 4);
			int sub = hex(t, i + 22, 4);
			int p = i + 26;
			if (command == CMD_LOOPBACK) {
				return new McRequest(true, fourE, serial, network, pc, io, station, command, sub, (byte) 0, null, 0, 0,
					t.substring(p).getBytes(StandardCharsets.US_ASCII));
			}
			String device = t.substring(p, p + 2);
			int start = HEX_DEVICES.contains(device) ? hex(t, p + 2, 6) : Integer.parseInt(t.substring(p + 2, p + 8));
			int count = hex(t, p + 8, 4);
			return new McRequest(true, fourE, serial, network, pc, io, station, command, sub, (byte) 0, device, start, count,
				t.substring(p + 12).getBytes(StandardCharsets.US_ASCII));
		}
		boolean fourE = f[0] == 0x54;
		int serial = fourE ? (f[2] & 0xFF) | (f[3] & 0xFF) << 8 : -1;
		int i = fourE ? 6 : 2;
		int network = f[i] & 0xFF;
		int pc = f[i + 1] & 0xFF;
		int io = (f[i + 2] & 0xFF) | (f[i + 3] & 0xFF) << 8;
		int station = f[i + 4] & 0xFF;
		int command = (f[i + 9] & 0xFF) | (f[i + 10] & 0xFF) << 8;
		int sub = (f[i + 11] & 0xFF) | (f[i + 12] & 0xFF) << 8;
		int p = i + 13;
		if (command == CMD_LOOPBACK) {
			return new McRequest(false, fourE, serial, network, pc, io, station, command, sub, (byte) 0, null, 0, 0, slice(f, p));
		}
		int start = (f[p] & 0xFF) | (f[p + 1] & 0xFF) << 8 | (f[p + 2] & 0xFF) << 16;
		byte deviceCode = f[p + 3];
		int count = (f[p + 4] & 0xFF) | (f[p + 5] & 0xFF) << 8;
		return new McRequest(false, fourE, serial, network, pc, io, station, command, sub, deviceCode, null, start, count, slice(f, p + 6));
	}

	/** 응답 프레임. endCode ≠ 0 이면 payload 는 무시하고 오류 정보를 담는다 */
	public static byte[] response(McRequest r, int endCode, byte[] payload) {
		byte[] data = endCode == 0 ? (payload != null ? payload : new byte[0]) : errorInfo(r);
		if (r.ascii()) {
			String head = (r.fourE() ? "D400" + String.format("%04X", r.serial()) + "0000" : "D000")
				+ String.format("%02X%02X%04X%02X%04X%04X", r.network(), r.pc(), r.io(), r.station(), 4 + data.length, endCode);
			byte[] text = head.getBytes(StandardCharsets.US_ASCII);
			byte[] out = new byte[text.length + data.length];
			System.arraycopy(text, 0, out, 0, text.length);
			System.arraycopy(data, 0, out, text.length, data.length);
			return out;
		}
		int head = r.fourE() ? 15 : 11;
		byte[] out = new byte[head + data.length];
		int i = 0;
		out[i++] = (byte) (r.fourE() ? 0xD4 : 0xD0);
		out[i++] = 0x00;
		if (r.fourE()) {
			out[i++] = (byte) r.serial();
			out[i++] = (byte) (r.serial() >> 8);
			out[i++] = 0x00;
			out[i++] = 0x00;
		}
		out[i++] = (byte) r.network();
		out[i++] = (byte) r.pc();
		out[i++] = (byte) r.io();
		out[i++] = (byte) (r.io() >> 8);
		out[i++] = (byte) r.station();
		int len = 2 + data.length;
		out[i++] = (byte) len;
		out[i++] = (byte) (len >> 8);
		out[i++] = (byte) endCode;
		out[i++] = (byte) (endCode >> 8);
		System.arraycopy(data, 0, out, i, data.length);
		return out;
	}

	private static byte[] errorInfo(McRequest r) {
		if (r.ascii()) {
			return String.format("%02X%02X%04X%02X%04X%04X", r.network(), r.pc(), r.io(), r.station(), r.command(), r.subCommand())
				.getBytes(StandardCharsets.US_ASCII);
		}
		return new byte[] { (byte) r.network(), (byte) r.pc(), (byte) r.io(), (byte) (r.io() >> 8), (byte) r.station(),
			(byte) r.command(), (byte) (r.command() >> 8), (byte) r.subCommand(), (byte) (r.subCommand() >> 8) };
	}

	private static int hex(String t, int from, int len) {
		return Integer.parseInt(t.substring(from, from + len), 16);
	}

	private static byte[] slice(byte[] f, int from) {
		byte[] out = new byte[Math.max(0, f.length - from)];
		System.arraycopy(f, from, out, 0, out.length);
		return out;
	}
}
