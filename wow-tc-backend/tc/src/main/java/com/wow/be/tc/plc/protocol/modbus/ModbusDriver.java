package com.wow.be.tc.plc.protocol.modbus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.Types.InterfaceType;
import com.wow.be.tc.interfs.PointInfo;
import com.wow.be.tc.plc.protocol.ByteOrder;
import com.wow.be.tc.plc.protocol.FrameExchanger;
import com.wow.be.tc.plc.protocol.PlcDriver;
import com.wow.be.tc.plc.protocol.PlannedRequest;
import com.wow.be.tc.plc.protocol.PlcOperation;
import com.wow.be.tc.plc.protocol.PlcResult;
import com.wow.be.tc.plc.protocol.PlcStatus;
import com.wow.be.tc.plc.protocol.PointType;
import com.wow.be.tc.plc.protocol.ProtocolDescriptor;
import com.wow.be.tc.plc.protocol.ValueCodec;
import com.wow.be.tc.plc.protocol.WireProtocol;
import com.wow.be.tc.plc.protocol.modbus.ModbusAddress.Table;

/**
 * Modbus TCP 드라이버 (docs/plc-protocol-design.md §6.2·§14). 프레임 조립·해석은 {@link ModbusFrames}, 주소는 {@link ModbusAddress}.
 *
 * <ul>
 *   <li>영역: 보유 레지스터(HR, 읽기·쓰기)·입력 레지스터(IR, 읽기만)는 숫자·STRING·BYTES, 코일(CO, 읽기·쓰기)·이산 입력(DI, 읽기만)은
 *       BOOL 만.</li>
 *   <li>읽기: 같은 영역의 가까운 점끼리 한 번으로 묶는다(빈 칸 레지스터 {@link #GAP_REGISTERS}·코일 {@link #GAP_COILS} 까지,
 *       {@code maxRegistersPerRequest}·{@code maxCoilsPerRequest} 까지). FC 1·2·3·4.</li>
 *   <li>쓰기: 바로 이어진 점끼리 묶어, 하나면 FC 6·5, 여럿이면 FC 16·15. 여러 프레임이면 차례로 보내고 예외 응답이면 멈춘다 —
 *       프레임 사이에는 원자적이지 않다.</li>
 *   <li>값: 레지스터 안 바이트 순서는 설정 {@code byteOrder}(기본 BIG), 32·64비트 값의 워드 순서는 {@code wordOrder}(기본 BIG,
 *       장비마다 다르다 — CDAB 장비는 LITTLE). 점에 순서 재정의가 있으면 그것을 쓴다.</li>
 *   <li>예외 응답 → {@link PlcStatus}(코드 = 예외 코드). 연결은 그대로 쓴다.</li>
 * </ul>
 */
public class ModbusDriver implements PlcDriver {

	public static final String PROTOCOL = "MODBUS_TCP";

	/** 규격상 한 번에 읽는 레지스터·코일 수의 상한 */
	static final int MAX_READ_REGISTERS = 125;

	static final int MAX_READ_COILS = 2000;

	/** 규격상 한 번에 쓰는 레지스터(FC 16)·코일(FC 15) 수의 상한 */
	static final int MAX_WRITE_REGISTERS = 123;

	static final int MAX_WRITE_COILS = 1968;

	/** 읽기를 묶을 때 사이에 끼어도 되는 빈 칸 — 요청 수를 줄이는 대신 조금 더 읽는다 */
	static final int GAP_REGISTERS = 8;

	static final int GAP_COILS = 16;

	private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_.\\-]{0,99}");

	private static final String KEYS = "unitId, byteOrder, wordOrder, addressBase, maxRegistersPerRequest, maxCoilsPerRequest, healthCheckIntervalSec";

	@Override
	public String protocol() {
		return PROTOCOL;
	}

	// ── 설정 ──

	/** @param legacyFrameFormat 쓰지 않는다(MELSEC 의 옛 필드) */
	@Override
	public Map<String, Object> normalizeSettings(Map<String, Object> settings, String legacyFrameFormat) {
		Map<String, Object> in = settings != null ? settings : Map.of();
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("unitId", number(in, "unitId", 1, 0, 255));
		out.put("byteOrder", order(in, "byteOrder").name());
		out.put("wordOrder", order(in, "wordOrder").name());
		out.put("addressBase", number(in, "addressBase", 1, 0, 1));
		out.put("maxRegistersPerRequest", number(in, "maxRegistersPerRequest", MAX_READ_REGISTERS, 1, MAX_READ_REGISTERS));
		out.put("maxCoilsPerRequest", number(in, "maxCoilsPerRequest", MAX_READ_COILS, 1, MAX_READ_COILS));
		// 쉬는 채널에 레지스터 하나 읽기를 보내 반쯤 죽은 연결을 찾는 주기(초). 0 이면 하지 않는다
		out.put("healthCheckIntervalSec", number(in, "healthCheckIntervalSec", 30, 0, 3600));
		for (String key : in.keySet()) {
			if (!out.containsKey(key)) {
				throw new IllegalArgumentException("Modbus 설정에 없는 항목입니다: " + key + " (" + KEYS + ")");
			}
		}
		return out;
	}

	private static ByteOrder order(Map<String, Object> in, String key) {
		Object v = in.get(key);
		if (v == null || v.toString().isBlank()) {
			return ByteOrder.BIG;
		}
		try {
			return ByteOrder.valueOf(v.toString().trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Modbus 설정 " + key + " 은 BIG 또는 LITTLE 입니다: " + v);
		}
	}

	private static int number(Map<String, Object> in, String key, int def, int min, int max) {
		Object v = in.get(key);
		if (v == null || v.toString().isBlank()) {
			return def;
		}
		int n;
		try {
			n = v instanceof Number num ? num.intValue() : Integer.parseInt(v.toString().trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Modbus 설정 " + key + " 은 숫자여야 합니다: " + v);
		}
		if (n < min || n > max) {
			throw new IllegalArgumentException("Modbus 설정 " + key + " 은 " + min + "~" + max + " 입니다: " + v);
		}
		return n;
	}

	private static int intOf(Map<String, Object> s, String key, int def) {
		Object v = s != null ? s.get(key) : null;
		return v instanceof Number n ? n.intValue() : v != null ? Integer.parseInt(v.toString()) : def;
	}

	private static ByteOrder orderOf(Map<String, Object> s, String key) {
		Object v = s != null ? s.get(key) : null;
		return v != null ? ByteOrder.valueOf(v.toString()) : ByteOrder.BIG;
	}

	// ── 점 검증 ──

	/**
	 * 인터페이스 저장 때는 커넥터의 {@code addressBase} 를 모르므로 번호 범위를 너그럽게(0 부터) 본다 — {@code addressBase} 1 에서
	 * {@code HR0} 같은 주소는 실행 때 {@code ERR_409_TC_INTERFACE_CONFIG} 로 걸린다.
	 */
	@Override
	public void validatePoint(PointInfo p, InterfaceType interfaceType) {
		validate(p, ModbusAddress.parse(p.getAddress(), 0), interfaceType);
	}

	private static void validate(PointInfo p, ModbusAddress a, InterfaceType interfaceType) {
		if (p.getName() == null || !NAME.matcher(p.getName()).matches()) {
			throw new IllegalArgumentException("점 이름은 영문자·_ 로 시작하는 영숫자·_ . - 입니다: " + p.getName());
		}
		if (p.getType() == null) {
			throw new IllegalArgumentException(p.getName() + ": 형(type)이 없습니다");
		}
		if (p.getCount() < 1) {
			throw new IllegalArgumentException(p.getName() + ": count 는 1 이상입니다");
		}
		if (interfaceType == InterfaceType.WRITE && a.table().isReadOnly()) {
			throw new IllegalArgumentException(p.getName() + ": " + a.table() + " 는 읽기만 됩니다 — 쓰기 인터페이스에는 HR·CO 를 쓰세요");
		}
		if (a.table().isBit()) {
			if (p.getType() != PointType.BOOL) {
				throw new IllegalArgumentException(p.getName() + ": 코일·이산 입력(" + a.table() + ")은 BOOL 로만 읽고 씁니다");
			}
			int max = interfaceType == InterfaceType.WRITE ? MAX_WRITE_COILS : MAX_READ_COILS;
			if (p.getCount() > max) {
				throw new IllegalArgumentException(p.getName() + ": 비트 수는 " + max + " 까지입니다");
			}
		} else {
			if (p.getType() == PointType.BOOL) {
				throw new IllegalArgumentException(p.getName() + ": 레지스터(" + a.table() + ")에는 BOOL 을 쓸 수 없습니다 — 코일(CO)·이산 입력(DI)을 쓰세요");
			}
			if (p.getType().hasLength() && p.getLength() < 1) {
				throw new IllegalArgumentException(p.getName() + ": " + p.getType() + " 는 length(바이트 수)가 필요합니다");
			}
			int regs = registers(p);
			if (regs > MAX_READ_REGISTERS) {
				throw new IllegalArgumentException(p.getName() + ": " + regs + "레지스터 — 한 점은 " + MAX_READ_REGISTERS + "레지스터까지입니다");
			}
			if (interfaceType == InterfaceType.WRITE && regs > MAX_WRITE_REGISTERS) {
				throw new IllegalArgumentException(p.getName() + ": " + regs + "레지스터 — 쓰기 한 점은 " + MAX_WRITE_REGISTERS + "레지스터까지입니다");
			}
		}
		if (a.offset() + units(p, a) - 1 > 0xFFFF) {
			throw new IllegalArgumentException(p.getName() + ": 주소 범위(0~65535)를 넘습니다");
		}
	}

	private static int registers(PointInfo p) {
		return ValueCodec.byteSpan(p.getType(), p.getCount(), p.getLength()) / 2;
	}

	/** 점이 차지하는 칸 수 — 레지스터 수 또는 비트 수 */
	private static int units(PointInfo p, ModbusAddress a) {
		return a.table().isBit() ? p.getCount() : registers(p);
	}

	// ── 실행 ──

	@Override
	public PlcResult execute(PlcOperation op, FrameExchanger exchanger) throws ToolControlException {
		List<Item> items;
		try {
			items = items(op);
		} catch (IllegalArgumentException e) {
			throw ToolControlException.of(ToolControlException.INTERFACE_MISCONFIGURED, e.getMessage());
		}
		Limits limits = new Limits(op.settings());
		ModbusFrames frames = new ModbusFrames(op.settings());
		Orders orders = orders(op.settings());
		return op.kind() == PlcOperation.Kind.READ
			? read(op, items, frames, orders, limits, exchanger)
			: write(op, items, frames, orders, limits, exchanger);
	}

	@Override
	public List<PlannedRequest> plan(PlcOperation op) {
		List<Item> items = items(op);
		Limits limits = new Limits(op.settings());
		ModbusFrames frames = new ModbusFrames(op.settings());
		List<PlannedRequest> out = new ArrayList<>();
		if (op.kind() == PlcOperation.Kind.READ) {
			for (Block block : blocks(items, limits.readRegisters, limits.readCoils)) {
				int fc = block.table.readFunction;
				out.add(PlannedRequest.of(out.size() + 1, "FC" + fc + " " + FUNCTION_NAMES.get(fc), target(block.table, block.first), block.units(),
					unit(block.table), block.items.stream().map(i -> i.point.getName()).toList(), frames.read(fc, block.first, block.units())));
			}
			return out;
		}
		for (Run run : runs(items, op.values(), orders(op.settings()), limits)) {
			WriteRequest w = writeRequest(frames, run);
			out.add(PlannedRequest.of(out.size() + 1, "FC" + w.fc + " " + FUNCTION_NAMES.get(w.fc), target(run.table, run.start), run.units(),
				unit(run.table), run.points, w.frame));
		}
		return out;
	}

	private static final Map<Integer, String> FUNCTION_NAMES = Map.of(1, "Read Coils", 2, "Read Discrete Inputs", 3, "Read Holding Registers",
		4, "Read Input Registers", 5, "Write Single Coil", 6, "Write Single Register", 15, "Write Multiple Coils", 16, "Write Multiple Registers");

	/** 영역 + 전선 번호(0부터) — 예 {@code HR0} */
	private static String target(Table table, int offset) {
		String prefix = switch (table) {
			case HOLDING_REGISTER -> "HR";
			case INPUT_REGISTER -> "IR";
			case COIL -> "CO";
			case DISCRETE_INPUT -> "DI";
		};
		return prefix + offset;
	}

	private static String unit(Table table) {
		return table.isBit() ? "비트" : "레지스터";
	}

	private List<Item> items(PlcOperation op) {
		int base = intOf(op.settings(), "addressBase", 1);
		InterfaceType type = op.kind() == PlcOperation.Kind.WRITE ? InterfaceType.WRITE : InterfaceType.READ;
		List<Item> items = new ArrayList<>();
		for (PointInfo p : op.points()) {
			ModbusAddress a = ModbusAddress.parse(p.getAddress(), base);
			validate(p, a, type);
			items.add(new Item(p, a, units(p, a)));
		}
		return items;
	}

	private static Orders orders(Map<String, Object> s) {
		return new Orders(orderOf(s, "byteOrder"), orderOf(s, "wordOrder"));
	}

	/** 요청 한 번의 한도 — 쓰기는 프로토콜 한도로 다시 자른다 */
	private static final class Limits {

		final int readRegisters;

		final int readCoils;

		final int writeRegisters;

		final int writeCoils;

		Limits(Map<String, Object> s) {
			readRegisters = intOf(s, "maxRegistersPerRequest", MAX_READ_REGISTERS);
			readCoils = intOf(s, "maxCoilsPerRequest", MAX_READ_COILS);
			writeRegisters = Math.min(readRegisters, MAX_WRITE_REGISTERS);
			writeCoils = Math.min(readCoils, MAX_WRITE_COILS);
		}
	}

	/** 쓰기 값을 프레임 단위로 — 값이 잘못되면 {@link IllegalArgumentException} */
	private static List<Run> runs(List<Item> items, Map<String, Object> values, Orders orders, Limits limits) {
		List<Run> runs = new ArrayList<>();
		for (Item item : items) {
			Object value = values != null ? values.get(item.point.getName()) : null;
			if (value == null) {
				throw new IllegalArgumentException("쓰기 값이 없습니다: " + item.point.getName());
			}
			runs.add(item.address.table().isBit() ? Run.bits(item, value) : Run.registers(item, value, orders));
		}
		return Run.merge(runs, limits.writeRegisters, limits.writeCoils);
	}

	/** 쓰기 프레임과, 응답이 되돌려 줄 값(단일 쓰기는 값, 여러 개는 개수) */
	private record WriteRequest(int fc, byte[] frame, int echo) {
	}

	private static WriteRequest writeRequest(ModbusFrames frames, Run run) {
		if (run.table.isBit()) {
			boolean single = run.bits.length == 1;
			return single
				? new WriteRequest(5, frames.writeCoil(run.start, run.bits[0]), run.bits[0] ? 0xFF00 : 0x0000)
				: new WriteRequest(15, frames.writeCoils(run.start, run.bits), run.bits.length);
		}
		boolean single = run.data.length == 2;
		return single
			? new WriteRequest(6, frames.writeRegister(run.start, run.data), (run.data[0] & 0xFF) << 8 | (run.data[1] & 0xFF))
			: new WriteRequest(16, frames.writeRegisters(run.start, run.data), run.data.length / 2);
	}

	private PlcResult read(PlcOperation op, List<Item> items, ModbusFrames frames, Orders orders, Limits limits,
			FrameExchanger exchanger) throws ToolControlException {
		Map<String, Object> values = new LinkedHashMap<>();
		for (Block block : blocks(items, limits.readRegisters, limits.readCoils)) {
			int fc = block.table.readFunction;
			int quantity = block.units();
			ModbusFrames.Reply reply = frames.reply(exchanger.exchange(op.reqId(), frames.read(fc, block.first, quantity)), fc);
			if (reply.exception() != 0) {
				return new PlcResult(values, status(reply.exception()));
			}
			byte[] data = ModbusFrames.readData(reply, block.table.isBit() ? (quantity + 7) / 8 : quantity * 2);
			for (Item item : block.items) {
				values.put(item.point.getName(), decode(item, block, data, orders));
			}
		}
		return new PlcResult(values, PlcStatus.ok(PROTOCOL));
	}

	private PlcResult write(PlcOperation op, List<Item> items, ModbusFrames frames, Orders orders, Limits limits,
			FrameExchanger exchanger) throws ToolControlException {
		for (Item item : items) {
			if (op.values() == null || op.values().get(item.point.getName()) == null) {
				throw ToolControlException.of(ToolControlException.INVALID_REQUEST, "쓰기 값이 없습니다: " + item.point.getName());
			}
		}
		List<Run> runs;
		try {
			runs = runs(items, op.values(), orders, limits);
		} catch (IllegalArgumentException e) {
			throw ToolControlException.of(ToolControlException.INVALID_REQUEST, "쓰기 값이 잘못되었습니다: " + e.getMessage());
		}
		for (Run run : runs) {
			WriteRequest w = writeRequest(frames, run);
			ModbusFrames.Reply reply = frames.reply(exchanger.exchange(op.reqId(), w.frame), w.fc);
			if (reply.exception() != 0) {
				return new PlcResult(Map.of(), status(reply.exception()));
			}
			ModbusFrames.checkEcho(reply, run.start, w.echo);
		}
		return new PlcResult(Map.of(), PlcStatus.ok(PROTOCOL));
	}

	/** 예외 코드 → 상태(코드 = 예외 코드) */
	public static PlcStatus status(int exception) {
		String why = switch (exception) {
			case 0x01 -> "지원하지 않는 함수";
			case 0x02 -> "잘못된 데이터 주소";
			case 0x03 -> "잘못된 데이터 값";
			case 0x04 -> "장치 오류";
			case 0x05 -> "처리 중(확인)";
			case 0x06 -> "장치 바쁨";
			case 0x0A -> "게이트웨이 경로 없음";
			case 0x0B -> "게이트웨이 대상 장치 응답 없음";
			default -> "알 수 없는 예외";
		};
		return new PlcStatus(PROTOCOL, exception, String.format("Modbus 예외 코드 0x%02X (%s)", exception, why));
	}

	@Override
	public ProtocolDescriptor describe() {
		List<PointType> register = java.util.Arrays.stream(PointType.values()).filter(t -> t != PointType.BOOL).toList();
		List<String> orders = List.of(ByteOrder.BIG.name(), ByteOrder.LITTLE.name());
		return new ProtocolDescriptor(PROTOCOL, "Modbus TCP", List.of(
			new ProtocolDescriptor.Setting("unitId", "NUMBER", 1, 0, 255, null, false, "단위 ID(게이트웨이 뒤 장치 번호)"),
			new ProtocolDescriptor.Setting("byteOrder", "ENUM", "BIG", null, null, orders, false, "레지스터 안 바이트 순서"),
			new ProtocolDescriptor.Setting("wordOrder", "ENUM", "BIG", null, null, orders, false,
				"32·64비트 값의 워드 순서 — CDAB 장비는 LITTLE"),
			new ProtocolDescriptor.Setting("addressBase", "ENUM", 1, null, null, List.of("0", "1"), false,
				"HR100 같은 번호를 0 부터 셀지 1 부터 셀지(40001 표기는 늘 1부터)"),
			new ProtocolDescriptor.Setting("maxRegistersPerRequest", "NUMBER", MAX_READ_REGISTERS, 1, MAX_READ_REGISTERS, null, false,
				"요청 한 번의 최대 레지스터 수"),
			new ProtocolDescriptor.Setting("maxCoilsPerRequest", "NUMBER", MAX_READ_COILS, 1, MAX_READ_COILS, null, false, "요청 한 번의 최대 코일 수"),
			new ProtocolDescriptor.Setting("healthCheckIntervalSec", "NUMBER", 30, 0, 3600, null, false,
				"쉬는 연결 확인(레지스터 읽기) 주기(초), 0 이면 끔")),
			List.of(new ProtocolDescriptor.Area("HR", "보유 레지스터", false, false, false, register),
				new ProtocolDescriptor.Area("IR", "입력 레지스터(읽기만)", false, false, true, register),
				new ProtocolDescriptor.Area("CO", "코일", true, false, false, List.of(PointType.BOOL)),
				new ProtocolDescriptor.Area("DI", "이산 입력(읽기만)", true, false, true, List.of(PointType.BOOL))),
			List.of("HR1", "40001", "IR10", "300010", "CO1", "DI5"), false);
	}

	@Override
	public WireProtocol wire(Map<String, Object> settings) {
		return new ModbusFrames(settings);
	}

	private static Object decode(Item item, Block block, byte[] data, Orders orders) {
		PointInfo p = item.point;
		int at = item.address.offset() - block.first;
		if (block.table.isBit()) {
			List<Object> bits = new ArrayList<>(p.getCount());
			for (int k = 0; k < p.getCount(); k++) {
				int i = at + k;
				bits.add(((data[i / 8] >> (i % 8)) & 1) == 1);
			}
			return p.getCount() == 1 ? bits.get(0) : bits;
		}
		return ValueCodec.decode(p.getType(), p.getCount(), p.getLength(), data, at * 2, orders.byteOf(p), orders.wordOf(p));
	}

	/** 커넥터 기본 순서 — 점에 재정의가 있으면 그것 */
	record Orders(ByteOrder byteOrder, ByteOrder wordOrder) {

		ByteOrder byteOf(PointInfo p) {
			return p.getByteOrder() != null ? p.getByteOrder() : byteOrder;
		}

		ByteOrder wordOf(PointInfo p) {
			return p.getWordOrder() != null ? p.getWordOrder() : wordOrder;
		}
	}

	// ── 묶기 ──

	/** 점 하나 — 주소와 차지하는 칸 수(레지스터 또는 비트) */
	record Item(PointInfo point, ModbusAddress address, int units) {

		int first() {
			return address.offset();
		}

		int last() {
			return address.offset() + units - 1;
		}
	}

	/** 읽기 한 번 — 같은 영역의 칸 범위 */
	static final class Block {

		final Table table;

		final int first;

		int last;

		final List<Item> items = new ArrayList<>();

		Block(Item item) {
			this.table = item.address.table();
			this.first = item.first();
			this.last = item.last();
			items.add(item);
		}

		int units() {
			return last - first + 1;
		}
	}

	static List<Block> blocks(List<Item> items, int maxRegisters, int maxCoils) {
		List<Item> sorted = new ArrayList<>(items);
		sorted.sort(Comparator.comparing((Item i) -> i.address.table()).thenComparingInt(Item::first));
		List<Block> blocks = new ArrayList<>();
		Block current = null;
		for (Item item : sorted) {
			Table table = item.address.table();
			int gap = table.isBit() ? GAP_COILS : GAP_REGISTERS;
			int max = table.isBit() ? maxCoils : maxRegisters;
			if (current != null && current.table == table && item.first() <= current.last + 1 + gap
					&& Math.max(current.last, item.last()) - current.first + 1 <= max) {
				current.last = Math.max(current.last, item.last());
				current.items.add(item);
				continue;
			}
			current = new Block(item);
			blocks.add(current);
		}
		return blocks;
	}

	/** 쓰기 한 번 — 레지스터(전선 순서 바이트) 또는 코일 */
	static final class Run {

		final Table table;

		final int start;

		byte[] data;

		boolean[] bits;

		/** 담긴 점 이름(미리보기) */
		final List<String> points = new ArrayList<>();

		private Run(Table table, int start, String point) {
			this.table = table;
			this.start = start;
			points.add(point);
		}

		static Run registers(Item item, Object value, Orders orders) {
			PointInfo p = item.point;
			Run run = new Run(item.address.table(), item.address.offset(), p.getName());
			run.data = ValueCodec.encode(p.getType(), p.getCount(), p.getLength(), value, orders.byteOf(p), orders.wordOf(p), p.getName());
			return run;
		}

		static Run bits(Item item, Object value) {
			PointInfo p = item.point;
			Run run = new Run(item.address.table(), item.address.offset(), p.getName());
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

		int units() {
			return table.isBit() ? bits.length : data.length / 2;
		}

		/** 같은 영역에서 바로 이어진 쓰기를 하나로(한도 안) */
		static List<Run> merge(List<Run> runs, int maxRegisters, int maxCoils) {
			List<Run> sorted = new ArrayList<>(runs);
			sorted.sort(Comparator.comparing((Run r) -> r.table).thenComparingInt(r -> r.start));
			List<Run> out = new ArrayList<>();
			for (Run r : sorted) {
				Run last = out.isEmpty() ? null : out.get(out.size() - 1);
				int max = r.table.isBit() ? maxCoils : maxRegisters;
				if (last != null && last.table == r.table && last.start + last.units() == r.start && last.units() + r.units() <= max) {
					if (r.table.isBit()) {
						boolean[] joined = new boolean[last.bits.length + r.bits.length];
						System.arraycopy(last.bits, 0, joined, 0, last.bits.length);
						System.arraycopy(r.bits, 0, joined, last.bits.length, r.bits.length);
						last.bits = joined;
					} else {
						byte[] joined = new byte[last.data.length + r.data.length];
						System.arraycopy(last.data, 0, joined, 0, last.data.length);
						System.arraycopy(r.data, 0, joined, last.data.length, r.data.length);
						last.data = joined;
					}
					last.points.addAll(r.points);
					continue;
				}
				out.add(r);
			}
			return out;
		}
	}
}
