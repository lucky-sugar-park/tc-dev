package com.wow.be.tc.plc.protocol.melsec;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.Types.InterfaceType;
import com.wow.be.tc.interfs.PointInfo;
import com.wow.be.tc.plc.protocol.ByteOrder;
import com.wow.be.tc.plc.protocol.FrameExchanger;
import com.wow.be.tc.plc.protocol.PlcDriver;
import com.wow.be.tc.plc.protocol.PlcOperation;
import com.wow.be.tc.plc.protocol.PlcResult;
import com.wow.be.tc.plc.protocol.PlcStatus;
import com.wow.be.tc.plc.protocol.PointType;
import com.wow.be.tc.plc.protocol.ValueCodec;

/**
 * MELSEC MC 프로토콜 3E 드라이버 (docs/plc-protocol-design.md §6.1).
 *
 * <ul>
 *   <li>읽기: 같은 디바이스의 가까운 점끼리 워드 일괄 읽기(0401/0000) 한 번으로 묶는다(빈 칸 {@link #MERGE_GAP} 워드까지,
 *       {@code maxWordsPerRequest} 까지). 비트 디바이스도 워드 단위로 읽어 비트를 꺼낸다.</li>
 *   <li>쓰기: 워드 디바이스는 같은 디바이스의 바로 이어진 점끼리 워드 일괄 쓰기(1401/0000), 비트 디바이스는 비트 단위 쓰기(1401/0001).
 *       여러 프레임이면 차례로 보내고 PLC 가 오류로 답하면 멈춘다 — 프레임 사이에는 원자적이지 않다.</li>
 *   <li>값은 리틀엔디안(워드 안·워드 순서 모두). 점의 순서 재정의가 있으면 그것을 쓴다.</li>
 *   <li>응답 검증: 서브헤더, 응답 데이터 길이 칸과 실제 길이, 종료 코드, 읽은 데이터 길이.</li>
 * </ul>
 */
public class MelsecDriver implements PlcDriver {

	public static final String PROTOCOL = "MELSEC_MC";

	public static final String FRAME_3E_BINARY = "3E_BINARY";

	public static final String FRAME_3E_ASCII = "3E_ASCII";

	/** 3E 워드 일괄 읽기·쓰기 한 번의 최대 워드 수 */
	static final int MAX_WORDS = 960;

	/** 비트 단위 쓰기 한 번의 최대 점 수 */
	static final int MAX_BITS = 7168;

	/** 읽기를 묶을 때 사이에 끼어도 되는 빈 워드 수 — 요청 수를 줄이는 대신 조금 더 읽는다 */
	static final int MERGE_GAP = 8;

	static final int CMD_READ = 0x0401;

	static final int CMD_WRITE = 0x1401;

	static final int SUB_WORD = 0x0000;

	static final int SUB_BIT = 0x0001;

	private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_.\\-]{0,99}");

	@Override
	public String protocol() {
		return PROTOCOL;
	}

	// ── 설정 ──

	@Override
	public Map<String, Object> normalizeSettings(Map<String, Object> settings, String legacyFrameFormat) {
		Map<String, Object> in = settings != null ? settings : Map.of();
		Map<String, Object> out = new LinkedHashMap<>();
		Object frame = in.get("frame");
		if (frame == null || frame.toString().isBlank()) {
			frame = "ASCII".equalsIgnoreCase(legacyFrameFormat) ? FRAME_3E_ASCII : FRAME_3E_BINARY;
		}
		String f = frame.toString().trim().toUpperCase();
		if (!f.equals(FRAME_3E_BINARY) && !f.equals(FRAME_3E_ASCII)) {
			throw new IllegalArgumentException("MELSEC frame 은 " + FRAME_3E_BINARY + " 또는 " + FRAME_3E_ASCII + " 입니다(4E 는 다음 단계): " + frame);
		}
		out.put("frame", f);
		out.put("networkNo", number(in, "networkNo", 0x00, 0, 0xFF));
		out.put("pcNo", number(in, "pcNo", 0xFF, 0, 0xFF));
		out.put("ioNo", number(in, "ioNo", 0x03FF, 0, 0xFFFF));
		out.put("stationNo", number(in, "stationNo", 0x00, 0, 0xFF));
		out.put("monitoringTimer", number(in, "monitoringTimer", 0x0010, 0, 0xFFFF));
		out.put("maxWordsPerRequest", number(in, "maxWordsPerRequest", MAX_WORDS, 1, MAX_WORDS));
		for (String key : in.keySet()) {
			if (!out.containsKey(key)) {
				throw new IllegalArgumentException("MELSEC 설정에 없는 항목입니다: " + key
					+ " (frame, networkNo, pcNo, ioNo, stationNo, monitoringTimer, maxWordsPerRequest)");
			}
		}
		return out;
	}

	private static int number(Map<String, Object> in, String key, int def, int min, int max) {
		Object v = in.get(key);
		if (v == null || v.toString().isBlank()) {
			return def;
		}
		int n;
		try {
			String s = v.toString().trim();
			n = v instanceof Number num ? num.intValue()
				: s.toLowerCase().startsWith("0x") ? Integer.parseInt(s.substring(2), 16) : Integer.parseInt(s);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("MELSEC 설정 " + key + " 은 숫자여야 합니다(16진은 0x…): " + v);
		}
		if (n < min || n > max) {
			throw new IllegalArgumentException("MELSEC 설정 " + key + " 은 " + min + "~" + max + " 입니다: " + v);
		}
		return n;
	}

	// ── 점 검증 ──

	@Override
	public void validatePoint(PointInfo p, InterfaceType interfaceType) {
		if (p.getName() == null || !NAME.matcher(p.getName()).matches()) {
			throw new IllegalArgumentException("점 이름은 영문자·_ 로 시작하는 영숫자·_ . - 입니다: " + p.getName());
		}
		if (p.getType() == null) {
			throw new IllegalArgumentException(p.getName() + ": 형(type)이 없습니다");
		}
		MelsecAddress a = MelsecAddress.parse(p.getAddress());
		if (p.getCount() < 1) {
			throw new IllegalArgumentException(p.getName() + ": count 는 1 이상입니다");
		}
		if (a.device().isBit()) {
			if (p.getType() != PointType.BOOL) {
				throw new IllegalArgumentException(p.getName() + ": 비트 디바이스(" + a.device() + ")는 BOOL 로만 읽고 씁니다");
			}
			if (p.getCount() > MAX_BITS) {
				throw new IllegalArgumentException(p.getName() + ": 비트 수는 " + MAX_BITS + " 까지입니다");
			}
			return;
		}
		if (p.getType() == PointType.BOOL) {
			throw new IllegalArgumentException(p.getName() + ": 워드 디바이스(" + a.device() + ")에는 BOOL 을 쓸 수 없습니다 — 비트 디바이스(M, X, B …)를 쓰세요");
		}
		if (p.getType().hasLength() && p.getLength() < 1) {
			throw new IllegalArgumentException(p.getName() + ": " + p.getType() + " 는 length(바이트 수)가 필요합니다");
		}
		int words = ValueCodec.byteSpan(p.getType(), p.getCount(), p.getLength()) / 2;
		if (words > MAX_WORDS) {
			throw new IllegalArgumentException(p.getName() + ": " + words + "워드 — 한 점은 " + MAX_WORDS + "워드까지입니다");
		}
	}

	// ── 실행 ──

	@Override
	public PlcResult execute(PlcOperation op, FrameExchanger exchanger) throws ToolControlException {
		Frames frames = new Frames(op.settings());
		List<Item> items = new ArrayList<>();
		for (PointInfo p : op.points()) {
			try {
				validatePoint(p, null);
			} catch (IllegalArgumentException e) {
				throw ToolControlException.of(ToolControlException.INTERFACE_MISCONFIGURED, e.getMessage());
			}
			items.add(new Item(p, MelsecAddress.parse(p.getAddress())));
		}
		return op.kind() == PlcOperation.Kind.READ ? read(op, items, frames, exchanger) : write(op, items, frames, exchanger);
	}

	private PlcResult read(PlcOperation op, List<Item> items, Frames frames, FrameExchanger exchanger) throws ToolControlException {
		Map<String, Object> values = new LinkedHashMap<>();
		for (Block block : blocks(items, frames.maxWords)) {
			int start = block.device.isBit() ? block.firstWord * 16 : block.firstWord;
			byte[] response = exchanger.exchange(op.reqId(), frames.request(CMD_READ, SUB_WORD, block.device, start, block.words(), null));
			Reply reply = frames.reply(response);
			if (reply.endCode != 0) {
				return new PlcResult(values, status(reply.endCode));
			}
			byte[] data = frames.words(reply, block.words());
			for (Item item : block.items) {
				values.put(item.point.getName(), decode(item, block, data));
			}
		}
		return new PlcResult(values, PlcStatus.ok(PROTOCOL));
	}

	private PlcResult write(PlcOperation op, List<Item> items, Frames frames, FrameExchanger exchanger) throws ToolControlException {
		List<Run> runs = new ArrayList<>();
		for (Item item : items) {
			Object value = op.values() != null ? op.values().get(item.point.getName()) : null;
			if (value == null) {
				throw ToolControlException.of(ToolControlException.INVALID_REQUEST, "쓰기 값이 없습니다: " + item.point.getName());
			}
			try {
				runs.add(item.address.device().isBit() ? Run.bits(item, value) : Run.words(item, value));
			} catch (IllegalArgumentException e) {
				throw ToolControlException.of(ToolControlException.INVALID_REQUEST, "쓰기 값이 잘못되었습니다: " + e.getMessage());
			}
		}
		for (Run run : Run.merge(runs, frames.maxWords)) {
			byte[] request = run.bit
				? frames.request(CMD_WRITE, SUB_BIT, run.device, run.start, run.bits.length, frames.bitData(run.bits))
				: frames.request(CMD_WRITE, SUB_WORD, run.device, run.start, run.data.length / 2, frames.wordData(run.data));
			Reply reply = frames.reply(exchanger.exchange(op.reqId(), request));
			if (reply.endCode != 0) {
				return new PlcResult(Map.of(), status(reply.endCode));
			}
		}
		return new PlcResult(Map.of(), PlcStatus.ok(PROTOCOL));
	}

	public static PlcStatus status(int endCode) {
		return endCode == 0 ? PlcStatus.ok(PROTOCOL) : new PlcStatus(PROTOCOL, endCode, String.format("MELSEC 종료 코드 0x%04X", endCode));
	}

	private static Object decode(Item item, Block block, byte[] data) {
		PointInfo p = item.point;
		if (item.address.device().isBit()) {
			List<Object> bits = new ArrayList<>(p.getCount());
			for (int k = 0; k < p.getCount(); k++) {
				int bitNo = item.address.number() + k;
				int w = (bitNo / 16 - block.firstWord) * 2;
				int word = (data[w] & 0xFF) | (data[w + 1] & 0xFF) << 8;
				bits.add(((word >> (bitNo % 16)) & 1) == 1);
			}
			return p.getCount() == 1 ? bits.get(0) : bits;
		}
		int offset = (item.address.number() - block.firstWord) * 2;
		return ValueCodec.decode(p.getType(), p.getCount(), p.getLength(), data, offset, order(p.getByteOrder()), order(p.getWordOrder()));
	}

	private static ByteOrder order(ByteOrder override) {
		return override != null ? override : ByteOrder.LITTLE;
	}

	// ── 묶기 ──

	/** 점 하나 — 주소와 차지하는 워드 범위(비트 디바이스는 비트를 담은 워드) */
	record Item(PointInfo point, MelsecAddress address) {

		int firstWord() {
			return address.device().isBit() ? address.number() / 16 : address.number();
		}

		int lastWord() {
			if (address.device().isBit()) {
				return (address.number() + point.getCount() - 1) / 16;
			}
			return address.number() + ValueCodec.byteSpan(point.getType(), point.getCount(), point.getLength()) / 2 - 1;
		}
	}

	/** 읽기 한 번 — 같은 디바이스의 워드 범위 */
	static final class Block {

		final MelsecDevice device;

		final int firstWord;

		int lastWord;

		final List<Item> items = new ArrayList<>();

		Block(Item first) {
			this.device = first.address.device();
			this.firstWord = first.firstWord();
			this.lastWord = first.lastWord();
			items.add(first);
		}

		int words() {
			return lastWord - firstWord + 1;
		}
	}

	static List<Block> blocks(List<Item> items, int maxWords) {
		List<Item> sorted = new ArrayList<>(items);
		sorted.sort(Comparator.comparing((Item i) -> i.address.device()).thenComparingInt(Item::firstWord));
		List<Block> blocks = new ArrayList<>();
		Block current = null;
		for (Item item : sorted) {
			if (current != null && current.device == item.address.device()
					&& item.firstWord() <= current.lastWord + 1 + MERGE_GAP
					&& Math.max(current.lastWord, item.lastWord()) - current.firstWord + 1 <= maxWords) {
				current.lastWord = Math.max(current.lastWord, item.lastWord());
				current.items.add(item);
				continue;
			}
			current = new Block(item);
			blocks.add(current);
		}
		return blocks;
	}

	/** 쓰기 한 번 — 워드(리틀엔디안 바이트) 또는 비트 */
	static final class Run {

		final MelsecDevice device;

		final boolean bit;

		final int start;

		byte[] data;

		boolean[] bits;

		private Run(MelsecDevice device, boolean bit, int start) {
			this.device = device;
			this.bit = bit;
			this.start = start;
		}

		static Run words(Item item, Object value) {
			PointInfo p = item.point;
			Run run = new Run(item.address.device(), false, item.address.number());
			run.data = ValueCodec.encode(p.getType(), p.getCount(), p.getLength(), value, order(p.getByteOrder()),
				order(p.getWordOrder()), p.getName());
			return run;
		}

		static Run bits(Item item, Object value) {
			PointInfo p = item.point;
			Run run = new Run(item.address.device(), true, item.address.number());
			run.bits = new boolean[p.getCount()];
			if (p.getCount() == 1) {
				run.bits[0] = ValueCodec.bool(value, p.getName());
			} else {
				if (!(value instanceof List<?> list) || list.size() != p.getCount()) {
					throw new IllegalArgumentException(p.getName() + ": 값 " + p.getCount() + "개짜리 배열이어야 합니다");
				}
				for (int i = 0; i < run.bits.length; i++) {
					run.bits[i] = ValueCodec.bool(list.get(i), p.getName() + "[" + i + "]");
				}
			}
			return run;
		}

		int end() {
			return bit ? start + bits.length : start + data.length / 2;
		}

		/** 같은 디바이스에서 바로 이어진 워드 쓰기를 하나로(한도 안). 비트 쓰기는 그대로 */
		static List<Run> merge(List<Run> runs, int maxWords) {
			List<Run> sorted = new ArrayList<>(runs);
			sorted.sort(Comparator.comparing((Run r) -> r.device).thenComparingInt(r -> r.start));
			List<Run> out = new ArrayList<>();
			for (Run r : sorted) {
				Run last = out.isEmpty() ? null : out.get(out.size() - 1);
				if (last != null && !last.bit && !r.bit && last.device == r.device && last.end() == r.start
						&& (last.data.length + r.data.length) / 2 <= maxWords) {
					byte[] joined = new byte[last.data.length + r.data.length];
					System.arraycopy(last.data, 0, joined, 0, last.data.length);
					System.arraycopy(r.data, 0, joined, last.data.length, r.data.length);
					last.data = joined;
					continue;
				}
				out.add(r);
			}
			return out;
		}
	}

	// ── 프레임 ──

	record Reply(int endCode, byte[] data, boolean ascii) {
	}

	/** 3E 프레임(BINARY·ASCII) 조립과 해석 — 커넥터 설정의 번호·타이머를 쓴다 */
	static final class Frames {

		final boolean ascii;

		final int network;

		final int pc;

		final int io;

		final int station;

		final int timer;

		final int maxWords;

		Frames(Map<String, Object> settings) {
			Map<String, Object> s = settings != null ? settings : Map.of();
			this.ascii = FRAME_3E_ASCII.equals(String.valueOf(s.getOrDefault("frame", FRAME_3E_BINARY)));
			this.network = intOf(s, "networkNo", 0x00);
			this.pc = intOf(s, "pcNo", 0xFF);
			this.io = intOf(s, "ioNo", 0x03FF);
			this.station = intOf(s, "stationNo", 0x00);
			this.timer = intOf(s, "monitoringTimer", 0x0010);
			this.maxWords = intOf(s, "maxWordsPerRequest", MAX_WORDS);
		}

		private static int intOf(Map<String, Object> s, String key, int def) {
			Object v = s.get(key);
			return v instanceof Number n ? n.intValue() : v != null ? Integer.parseInt(v.toString()) : def;
		}

		/**
		 * 요청 프레임. data 는 이미 프레임 형식에 맞춘 것 — BINARY 면 바이트, ASCII 면 문자 바이트({@link #wordData}·{@link #bitData}).
		 */
		byte[] request(int command, int subCommand, MelsecDevice device, int start, int count, byte[] data) {
			int dataLen = data == null ? 0 : data.length;
			if (ascii) {
				String number = device.isHexNumber() ? String.format("%06X", start) : String.format("%06d", start);
				String body = String.format("%04X%04X%04X", timer, command, subCommand) + device.asciiCode() + number
					+ String.format("%04X", count);
				String head = String.format("5000%02X%02X%04X%02X%04X", network, pc, io, station, body.length() + dataLen);
				byte[] text = (head + body).getBytes(StandardCharsets.US_ASCII);
				byte[] out = new byte[text.length + dataLen];
				System.arraycopy(text, 0, out, 0, text.length);
				if (data != null) {
					System.arraycopy(data, 0, out, text.length, dataLen);
				}
				return out;
			}
			byte[] out = new byte[21 + dataLen];
			int i = 0;
			out[i++] = 0x50;
			out[i++] = 0x00;
			out[i++] = (byte) network;
			out[i++] = (byte) pc;
			out[i++] = (byte) io;
			out[i++] = (byte) (io >> 8);
			out[i++] = (byte) station;
			int len = 12 + dataLen;
			out[i++] = (byte) len;
			out[i++] = (byte) (len >> 8);
			out[i++] = (byte) timer;
			out[i++] = (byte) (timer >> 8);
			out[i++] = (byte) command;
			out[i++] = (byte) (command >> 8);
			out[i++] = (byte) subCommand;
			out[i++] = (byte) (subCommand >> 8);
			out[i++] = (byte) start;
			out[i++] = (byte) (start >> 8);
			out[i++] = (byte) (start >> 16);
			out[i++] = device.binaryCode();
			out[i++] = (byte) count;
			out[i++] = (byte) (count >> 8);
			if (data != null) {
				System.arraycopy(data, 0, out, i, dataLen);
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

		/** 응답 프레임 검증·해석 — 서브헤더, 응답 데이터 길이 칸, 종료 코드 */
		Reply reply(byte[] frame) throws ToolControlException {
			if (ascii) {
				String text = new String(frame, StandardCharsets.US_ASCII);
				if (text.length() < 22 || !text.startsWith("D000")) {
					throw bad("ASCII 응답 머리가 맞지 않습니다", frame);
				}
				int len = hex(text.substring(14, 18), frame);
				if (len != text.length() - 18) {
					throw bad("응답 데이터 길이 칸(" + len + ")과 실제 길이(" + (text.length() - 18) + ")가 다릅니다", frame);
				}
				int endCode = hex(text.substring(18, 22), frame);
				return new Reply(endCode, text.substring(22).getBytes(StandardCharsets.US_ASCII), true);
			}
			if (frame.length < 11 || (frame[0] & 0xFF) != 0xD0 || frame[1] != 0x00) {
				throw bad("BINARY 응답 머리가 맞지 않습니다", frame);
			}
			int len = (frame[7] & 0xFF) | (frame[8] & 0xFF) << 8;
			if (len != frame.length - 9) {
				throw bad("응답 데이터 길이 칸(" + len + ")과 실제 길이(" + (frame.length - 9) + ")가 다릅니다", frame);
			}
			int endCode = (frame[9] & 0xFF) | (frame[10] & 0xFF) << 8;
			byte[] data = new byte[frame.length - 11];
			System.arraycopy(frame, 11, data, 0, data.length);
			return new Reply(endCode, data, false);
		}

		/** 읽은 워드를 리틀엔디안 바이트로 — 개수가 맞는지 본다 */
		byte[] words(Reply reply, int words) throws ToolControlException {
			if (!reply.ascii) {
				if (reply.data.length != words * 2) {
					throw bad("읽은 데이터가 " + reply.data.length + "바이트 — " + (words * 2) + "바이트여야 합니다", reply.data);
				}
				return reply.data;
			}
			String text = new String(reply.data, StandardCharsets.US_ASCII);
			if (text.length() != words * 4) {
				throw bad("읽은 데이터가 " + text.length() + "자 — " + (words * 4) + "자여야 합니다", reply.data);
			}
			byte[] out = new byte[words * 2];
			for (int i = 0; i < words; i++) {
				int w = hex(text.substring(i * 4, i * 4 + 4), reply.data);
				out[i * 2] = (byte) w;
				out[i * 2 + 1] = (byte) (w >> 8);
			}
			return out;
		}

		private static int hex(String s, byte[] frame) throws ToolControlException {
			try {
				return Integer.parseInt(s, 16);
			} catch (NumberFormatException e) {
				throw bad("16진 숫자가 아닙니다: " + s, frame);
			}
		}

		private static ToolControlException bad(String why, byte[] frame) {
			return ToolControlException.of(ToolControlException.PLC_ERROR, "MELSEC 응답을 해석할 수 없습니다 — " + why);
		}
	}
}
