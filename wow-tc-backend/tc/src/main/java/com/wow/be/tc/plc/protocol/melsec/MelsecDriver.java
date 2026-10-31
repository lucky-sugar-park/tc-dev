package com.wow.be.tc.plc.protocol.melsec;

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
import com.wow.be.tc.plc.protocol.WireProtocol;

/**
 * MELSEC MC 프로토콜 3E·4E 드라이버 (docs/plc-protocol-design.md §6.1). 프레임 조립·해석은 {@link McFrames}.
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

	public static final String FRAME_4E_BINARY = "4E_BINARY";

	public static final String FRAME_4E_ASCII = "4E_ASCII";

	private static final java.util.Set<String> FRAMES = java.util.Set.of(FRAME_3E_BINARY, FRAME_3E_ASCII, FRAME_4E_BINARY, FRAME_4E_ASCII);

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
		if (!FRAMES.contains(f)) {
			throw new IllegalArgumentException("MELSEC frame 은 " + FRAMES + " 중 하나입니다: " + frame);
		}
		out.put("frame", f);
		out.put("networkNo", number(in, "networkNo", 0x00, 0, 0xFF));
		out.put("pcNo", number(in, "pcNo", 0xFF, 0, 0xFF));
		out.put("ioNo", number(in, "ioNo", 0x03FF, 0, 0xFFFF));
		out.put("stationNo", number(in, "stationNo", 0x00, 0, 0xFF));
		out.put("monitoringTimer", number(in, "monitoringTimer", 0x0010, 0, 0xFFFF));
		out.put("maxWordsPerRequest", number(in, "maxWordsPerRequest", MAX_WORDS, 1, MAX_WORDS));
		// 쉬는 채널에 루프백(0619)을 보내 반쯤 죽은 연결을 찾는 주기(초). 0 이면 하지 않는다(#12)
		out.put("healthCheckIntervalSec", number(in, "healthCheckIntervalSec", 30, 0, 3600));
		for (String key : in.keySet()) {
			if (!out.containsKey(key)) {
				throw new IllegalArgumentException("MELSEC 설정에 없는 항목입니다: " + key
					+ " (frame, networkNo, pcNo, ioNo, stationNo, monitoringTimer, maxWordsPerRequest, healthCheckIntervalSec)");
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
		McFrames frames = new McFrames(op.settings());
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

	private PlcResult read(PlcOperation op, List<Item> items, McFrames frames, FrameExchanger exchanger) throws ToolControlException {
		Map<String, Object> values = new LinkedHashMap<>();
		for (Block block : blocks(items, frames.maxWords)) {
			int start = block.device.isBit() ? block.firstWord * 16 : block.firstWord;
			byte[] response = exchanger.exchange(op.reqId(), frames.request(CMD_READ, SUB_WORD, block.device, start, block.words(), null));
			McFrames.McReply reply = frames.reply(response);
			if (reply.endCode() != 0) {
				return new PlcResult(values, status(reply.endCode()));
			}
			byte[] data = frames.words(reply, block.words());
			for (Item item : block.items) {
				values.put(item.point.getName(), decode(item, block, data));
			}
		}
		return new PlcResult(values, PlcStatus.ok(PROTOCOL));
	}

	private PlcResult write(PlcOperation op, List<Item> items, McFrames frames, FrameExchanger exchanger) throws ToolControlException {
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
			McFrames.McReply reply = frames.reply(exchanger.exchange(op.reqId(), request));
			if (reply.endCode() != 0) {
				return new PlcResult(Map.of(), status(reply.endCode()));
			}
		}
		return new PlcResult(Map.of(), PlcStatus.ok(PROTOCOL));
	}

	@Override
	public WireProtocol wire(Map<String, Object> settings) {
		return new McFrames(settings);
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

}
