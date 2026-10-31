package com.wow.be.tc.plc.protocol.melsec;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.wow.be.tc.ToolControlException;

/**
 * MC 프로토콜 3E·4E 프레임(BINARY·ASCII) 조립과 해석 (docs/plc-protocol-design.md §6.1).
 *
 * <ul>
 *   <li>4E 는 3E 머리에 일련번호(2바이트)와 예비(2바이트)가 더해진다 — 응답이 요청의 일련번호를 그대로 돌려줘, 늦게 온 응답을
 *       연결을 끊지 않고도 가려낼 수 있다. 일련번호는 연결(채널)이 정하므로 여기서는 0 으로 만들고 {@link #setSerial} 로 넣는다.</li>
 *   <li>응답 길이 계산({@link #responseLength})은 어댑터의 디코더와 연결 시험이 함께 쓴다 — 3E·4E 를 서브헤더로 가린다.</li>
 * </ul>
 */
public final class McFrames {

	public static final int CMD_LOOPBACK = 0x0619;

	/** 루프백 시험 데이터 — MC 규약상 0-9·A-F 문자 */
	static final String LOOPBACK_DATA = "ABCDE";

	final boolean ascii;

	final boolean fourE;

	final int network;

	final int pc;

	final int io;

	final int station;

	final int timer;

	final int maxWords;

	public McFrames(Map<String, Object> settings) {
		Map<String, Object> s = settings != null ? settings : Map.of();
		String frame = String.valueOf(s.getOrDefault("frame", MelsecDriver.FRAME_3E_BINARY));
		this.ascii = frame.endsWith("ASCII");
		this.fourE = frame.startsWith("4E");
		this.network = intOf(s, "networkNo", 0x00);
		this.pc = intOf(s, "pcNo", 0xFF);
		this.io = intOf(s, "ioNo", 0x03FF);
		this.station = intOf(s, "stationNo", 0x00);
		this.timer = intOf(s, "monitoringTimer", 0x0010);
		this.maxWords = intOf(s, "maxWordsPerRequest", MelsecDriver.MAX_WORDS);
	}

	private static int intOf(Map<String, Object> s, String key, int def) {
		Object v = s.get(key);
		return v instanceof Number n ? n.intValue() : v != null ? Integer.parseInt(v.toString()) : def;
	}

	// ── 요청 ──

	/** 디바이스 지정 명령(읽기·쓰기). data 는 이미 프레임 형식에 맞춘 것({@link #wordData}·{@link #bitData}) */
	byte[] request(int command, int subCommand, MelsecDevice device, int start, int count, byte[] data) {
		if (ascii) {
			String number = device.isHexNumber() ? String.format("%06X", start) : String.format("%06d", start);
			String tail = device.asciiCode() + number + String.format("%04X", count);
			return requestAscii(command, subCommand, tail, data);
		}
		byte[] tail = new byte[6];
		tail[0] = (byte) start;
		tail[1] = (byte) (start >> 8);
		tail[2] = (byte) (start >> 16);
		tail[3] = device.binaryCode();
		tail[4] = (byte) count;
		tail[5] = (byte) (count >> 8);
		return requestBinary(command, subCommand, tail, data);
	}

	/** 루프백 시험(0619) 요청 — 연결 확인용 */
	public byte[] loopback() {
		byte[] text = LOOPBACK_DATA.getBytes(StandardCharsets.US_ASCII);
		if (ascii) {
			return requestAscii(CMD_LOOPBACK, 0, String.format("%04X", text.length) + LOOPBACK_DATA, null);
		}
		byte[] body = new byte[2 + text.length];
		body[0] = (byte) text.length;
		body[1] = (byte) (text.length >> 8);
		System.arraycopy(text, 0, body, 2, text.length);
		return requestBinary(CMD_LOOPBACK, 0, body, null);
	}

	/** 루프백 응답이 정상인지(종료 코드 0, 보낸 데이터를 그대로 돌려줌) */
	public boolean loopbackOk(byte[] response) {
		try {
			McReply reply = reply(response);
			if (reply.endCode() != 0) {
				return false;
			}
			String expected = ascii ? String.format("%04X", LOOPBACK_DATA.length()) + LOOPBACK_DATA : null;
			if (ascii) {
				return expected.equals(new String(reply.data(), StandardCharsets.US_ASCII));
			}
			byte[] d = reply.data();
			int n = (d[0] & 0xFF) | (d[1] & 0xFF) << 8;
			return n == LOOPBACK_DATA.length() && LOOPBACK_DATA.equals(new String(d, 2, n, StandardCharsets.US_ASCII));
		} catch (ToolControlException | RuntimeException e) {
			return false;
		}
	}

	private byte[] requestBinary(int command, int subCommand, byte[] tail, byte[] data) {
		int dataLen = data == null ? 0 : data.length;
		int head = fourE ? 13 : 9;
		byte[] out = new byte[head + 6 + tail.length + dataLen];
		int i = 0;
		out[i++] = (byte) (fourE ? 0x54 : 0x50);
		out[i++] = 0x00;
		if (fourE) {
			i += 4;  // 일련번호(나중에 채움)·예비
		}
		out[i++] = (byte) network;
		out[i++] = (byte) pc;
		out[i++] = (byte) io;
		out[i++] = (byte) (io >> 8);
		out[i++] = (byte) station;
		int len = 6 + tail.length + dataLen;
		out[i++] = (byte) len;
		out[i++] = (byte) (len >> 8);
		out[i++] = (byte) timer;
		out[i++] = (byte) (timer >> 8);
		out[i++] = (byte) command;
		out[i++] = (byte) (command >> 8);
		out[i++] = (byte) subCommand;
		out[i++] = (byte) (subCommand >> 8);
		System.arraycopy(tail, 0, out, i, tail.length);
		i += tail.length;
		if (data != null) {
			System.arraycopy(data, 0, out, i, dataLen);
		}
		return out;
	}

	private byte[] requestAscii(int command, int subCommand, String tail, byte[] data) {
		int dataLen = data == null ? 0 : data.length;
		String body = String.format("%04X%04X%04X", timer, command, subCommand) + tail;
		String head = (fourE ? "5400" + "0000" + "0000" : "5000")
			+ String.format("%02X%02X%04X%02X%04X", network, pc, io, station, body.length() + dataLen);
		byte[] text = (head + body).getBytes(StandardCharsets.US_ASCII);
		byte[] out = new byte[text.length + dataLen];
		System.arraycopy(text, 0, out, 0, text.length);
		if (data != null) {
			System.arraycopy(data, 0, out, text.length, dataLen);
		}
		return out;
	}

	/** 워드 쓰기 데이터 — 리틀엔디안 바이트를 프레임 형식으로(ASCII 는 워드마다 16진 4자리) */
	byte[] wordData(byte[] littleEndian) {
		if (!ascii) {
			return littleEndian;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < littleEndian.length; i += 2) {
			sb.append(String.format("%04X", (littleEndian[i] & 0xFF) | (littleEndian[i + 1] & 0xFF) << 8));
		}
		return sb.toString().getBytes(StandardCharsets.US_ASCII);
	}

	/** 비트 쓰기 데이터 — BINARY 는 한 바이트에 두 점(앞 점이 위 4비트), ASCII 는 점마다 '0'/'1' */
	byte[] bitData(boolean[] bits) {
		if (ascii) {
			StringBuilder sb = new StringBuilder();
			for (boolean b : bits) {
				sb.append(b ? '1' : '0');
			}
			return sb.toString().getBytes(StandardCharsets.US_ASCII);
		}
		byte[] out = new byte[(bits.length + 1) / 2];
		for (int k = 0; k < bits.length; k++) {
			if (bits[k]) {
				out[k / 2] |= (byte) (k % 2 == 0 ? 0x10 : 0x01);
			}
		}
		return out;
	}

	// ── 일련번호(4E) ──

	/** 4E 요청인가 — 서브헤더로 본다(BINARY 54 00, ASCII "5400") */
	public static boolean isFourERequest(byte[] frame) {
		return frame.length >= 4 && ((frame[0] == 0x54 && frame[1] == 0x00) || (frame[0] == '5' && frame[1] == '4' && frame[2] == '0' && frame[3] == '0'));
	}

	/** 4E 요청에 일련번호를 넣는다(BINARY 리틀엔디안 2바이트, ASCII 16진 4자리). 3E 면 그대로 */
	public static void setSerial(byte[] frame, int serial) {
		if (frame.length >= 4 && frame[0] == 0x54 && frame[1] == 0x00) {
			frame[2] = (byte) serial;
			frame[3] = (byte) (serial >> 8);
		} else if (isFourERequest(frame)) {
			byte[] hex = String.format("%04X", serial & 0xFFFF).getBytes(StandardCharsets.US_ASCII);
			System.arraycopy(hex, 0, frame, 4, 4);
		}
	}

	/** 응답의 일련번호. 3E 응답이면 -1 */
	public static int serialOf(byte[] response) {
		if (response.length >= 4 && (response[0] & 0xFF) == 0xD4 && response[1] == 0x00) {
			return (response[2] & 0xFF) | (response[3] & 0xFF) << 8;
		}
		if (response.length >= 8 && response[0] == 'D' && response[1] == '4' && response[2] == '0' && response[3] == '0') {
			try {
				return Integer.parseInt(new String(response, 4, 4, StandardCharsets.US_ASCII), 16);
			} catch (NumberFormatException e) {
				return -1;
			}
		}
		return -1;
	}

	// ── 응답 ──

	/**
	 * 받은 바이트의 앞에서 응답 프레임 하나의 길이. 아직 덜 왔으면 0, 응답 머리가 아니면 -1(버린다).
	 * BINARY: D0 00(3E, 길이 칸 7-8) · D4 00(4E, 길이 칸 11-12). ASCII: "D000"(길이 14-17) · "D400"(길이 22-25), 길이는 16진.
	 */
	public static int responseLength(byte[] buf, int available, boolean ascii) {
		if (ascii) {
			if (available < 4) {
				return 0;
			}
			boolean three = buf[0] == 'D' && buf[1] == '0' && buf[2] == '0' && buf[3] == '0';
			boolean four = buf[0] == 'D' && buf[1] == '4' && buf[2] == '0' && buf[3] == '0';
			if (!three && !four) {
				return -1;
			}
			int at = three ? 14 : 22;
			if (available < at + 4) {
				return 0;
			}
			int len;
			try {
				len = Integer.parseInt(new String(buf, at, 4, StandardCharsets.US_ASCII), 16);
			} catch (NumberFormatException e) {
				return -1;
			}
			int total = at + 4 + len;
			return available < total ? 0 : total;
		}
		if (available < 2) {
			return 0;
		}
		boolean three = (buf[0] & 0xFF) == 0xD0 && buf[1] == 0x00;
		boolean four = (buf[0] & 0xFF) == 0xD4 && buf[1] == 0x00;
		if (!three && !four) {
			return -1;
		}
		int at = three ? 7 : 11;
		if (available < at + 2) {
			return 0;
		}
		int total = at + 2 + ((buf[at] & 0xFF) | (buf[at + 1] & 0xFF) << 8);
		return available < total ? 0 : total;
	}

	/** 응답 프레임 검증·해석 — 서브헤더, 응답 데이터 길이 칸, 종료 코드 */
	McReply reply(byte[] frame) throws ToolControlException {
		if (ascii) {
			String text = new String(frame, StandardCharsets.US_ASCII);
			boolean four = text.startsWith("D400");
			int at = four ? 22 : 14;
			if (text.length() < at + 8 || !(text.startsWith("D000") || four)) {
				throw bad("ASCII 응답 머리가 맞지 않습니다");
			}
			int len = hex(text.substring(at, at + 4));
			if (len != text.length() - at - 4) {
				throw bad("응답 데이터 길이 칸(" + len + ")과 실제 길이(" + (text.length() - at - 4) + ")가 다릅니다");
			}
			int endCode = hex(text.substring(at + 4, at + 8));
			return new McReply(endCode, text.substring(at + 8).getBytes(StandardCharsets.US_ASCII), true);
		}
		boolean four = frame.length >= 2 && (frame[0] & 0xFF) == 0xD4 && frame[1] == 0x00;
		boolean three = frame.length >= 2 && (frame[0] & 0xFF) == 0xD0 && frame[1] == 0x00;
		int at = four ? 11 : 7;
		if ((!three && !four) || frame.length < at + 4) {
			throw bad("BINARY 응답 머리가 맞지 않습니다");
		}
		int len = (frame[at] & 0xFF) | (frame[at + 1] & 0xFF) << 8;
		if (len != frame.length - at - 2) {
			throw bad("응답 데이터 길이 칸(" + len + ")과 실제 길이(" + (frame.length - at - 2) + ")가 다릅니다");
		}
		int endCode = (frame[at + 2] & 0xFF) | (frame[at + 3] & 0xFF) << 8;
		byte[] data = new byte[frame.length - at - 4];
		System.arraycopy(frame, at + 4, data, 0, data.length);
		return new McReply(endCode, data, false);
	}

	/** 읽은 워드를 리틀엔디안 바이트로 — 개수가 맞는지 본다 */
	byte[] words(McReply reply, int words) throws ToolControlException {
		if (!reply.ascii()) {
			if (reply.data().length != words * 2) {
				throw bad("읽은 데이터가 " + reply.data().length + "바이트 — " + (words * 2) + "바이트여야 합니다");
			}
			return reply.data();
		}
		String text = new String(reply.data(), StandardCharsets.US_ASCII);
		if (text.length() != words * 4) {
			throw bad("읽은 데이터가 " + text.length() + "자 — " + (words * 4) + "자여야 합니다");
		}
		byte[] out = new byte[words * 2];
		for (int i = 0; i < words; i++) {
			int w = hex(text.substring(i * 4, i * 4 + 4));
			out[i * 2] = (byte) w;
			out[i * 2 + 1] = (byte) (w >> 8);
		}
		return out;
	}

	private static int hex(String s) throws ToolControlException {
		try {
			return Integer.parseInt(s, 16);
		} catch (NumberFormatException e) {
			throw bad("16진 숫자가 아닙니다: " + s);
		}
	}

	private static ToolControlException bad(String why) {
		return ToolControlException.of(ToolControlException.PLC_ERROR, "MELSEC 응답을 해석할 수 없습니다 — " + why);
	}

	record McReply(int endCode, byte[] data, boolean ascii) {
	}
}
