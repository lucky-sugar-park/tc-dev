package com.wow.be.tc.plc.protocol.opcua;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ushort;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.ULong;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.WriteValue;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.Types.InterfaceType;
import com.wow.be.tc.interfs.PointInfo;
import com.wow.be.tc.plc.protocol.FrameExchanger;
import com.wow.be.tc.plc.protocol.PlannedRequest;
import com.wow.be.tc.plc.protocol.PlcDriver;
import com.wow.be.tc.plc.protocol.PlcOperation;
import com.wow.be.tc.plc.protocol.PlcResult;
import com.wow.be.tc.plc.protocol.PlcStatus;
import com.wow.be.tc.plc.protocol.PointType;
import com.wow.be.tc.plc.protocol.ProtocolDescriptor;
import com.wow.be.tc.plc.protocol.WireProtocol;

/**
 * OPC UA 드라이버 (docs/opcua-design.md). MELSEC·Modbus 와 달리 프레임을 만들지 않는다 — 세션은 Milo 클라이언트가 들고,
 * 어댑터({@code OpcUaAdapter})가 {@link #run} 으로 작업을 실행한다. 이 클래스는 설정·점 검증·미리보기·값 변환을 맡는다.
 *
 * <ul>
 *   <li>점 주소 = NodeId 문자열({@code ns=2;s=Line1.Temp}). 형은 OPC UA 기본 형과 1:1(O4), {@code count > 1} 이면 배열.</li>
 *   <li>읽기 = Read 서비스, 쓰기 = Write 서비스 — 노드가 {@code maxNodesPerRequest} 를 넘으면 나눈다(O5).</li>
 *   <li>노드마다 StatusCode — 첫 나쁜 코드를 {@code plcStatus} 로, 값은 좋은 노드만(O6).</li>
 * </ul>
 */
public class OpcUaDriver implements PlcDriver {

	public static final String PROTOCOL = "OPC_UA";

	public static final int DEFAULT_PORT = 4840;

	static final long DEFAULT_REQUEST_TIMEOUT_MS = 5_000;

	static final long DEFAULT_SESSION_TIMEOUT_MS = 60_000;

	static final int MAX_NODES = 500;

	public static final String ACCESS_READ = "READ";

	public static final String ACCESS_SUBSCRIBE = "SUBSCRIBE";

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
		Object path = in.get("endpointPath");
		String p = path != null ? path.toString().trim() : "";
		if (p.length() > 200 || p.contains(" ")) {
			throw new IllegalArgumentException("OPC UA endpointPath 는 공백 없는 200자까지입니다: " + path);
		}
		out.put("endpointPath", p);
		out.put("requestTimeoutMs", number(in, "requestTimeoutMs", (int) DEFAULT_REQUEST_TIMEOUT_MS, 100, 600_000));
		out.put("sessionTimeoutMs", number(in, "sessionTimeoutMs", (int) DEFAULT_SESSION_TIMEOUT_MS, 10_000, 3_600_000));
		out.put("maxNodesPerRequest", number(in, "maxNodesPerRequest", MAX_NODES, 1, 10_000));
		out.put("publishingIntervalMs", number(in, "publishingIntervalMs", 1000, 50, 3_600_000));
		out.put("samplingIntervalMs", number(in, "samplingIntervalMs", 500, 0, 3_600_000));
		for (String key : in.keySet()) {
			if (!out.containsKey(key)) {
				throw new IllegalArgumentException("OPC UA 설정에 없는 항목입니다: " + key
					+ " (endpointPath, requestTimeoutMs, sessionTimeoutMs, maxNodesPerRequest, publishingIntervalMs, samplingIntervalMs)");
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
			n = v instanceof Number num ? num.intValue() : Integer.parseInt(v.toString().trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("OPC UA 설정 " + key + " 은 숫자여야 합니다: " + v);
		}
		if (n < min || n > max) {
			throw new IllegalArgumentException("OPC UA 설정 " + key + " 은 " + min + "~" + max + " 입니다: " + v);
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
		nodeId(p);
		if (p.getCount() < 1 || p.getCount() > 10_000) {
			throw new IllegalArgumentException(p.getName() + ": count 는 1~10000 입니다");
		}
		if (p.getByteOrder() != null || p.getWordOrder() != null) {
			throw new IllegalArgumentException(p.getName() + ": OPC UA 점에는 바이트·워드 순서를 쓰지 않습니다(값의 형은 서버가 정한다)");
		}
	}

	/** @throws IllegalArgumentException NodeId 문법이 틀림(400) */
	public static NodeId nodeId(PointInfo p) {
		String a = p.getAddress() != null ? p.getAddress().trim() : "";
		if (a.isEmpty()) {
			throw new IllegalArgumentException(p.getName() + ": 주소(NodeId)가 없습니다");
		}
		return NodeId.parseSafe(a).orElseThrow(() -> new IllegalArgumentException(p.getName()
			+ ": OPC UA 주소는 NodeId 입니다 — ns=2;s=Line1.Temp, ns=2;i=1001, i=2258: " + a));
	}

	// ── 실행 ──

	/** 프레임 교환으로는 실행하지 않는다 — OPC UA 어댑터가 {@link #run} 을 부른다 */
	@Override
	public PlcResult execute(PlcOperation op, FrameExchanger exchanger) throws ToolControlException {
		throw ToolControlException.of(ToolControlException.PLC_UNAVAILABLE, "OPC UA 는 세션 어댑터로만 실행합니다 — 커넥터를 게시하세요");
	}

	/** 작업을 Milo 클라이언트로 실행한다 */
	public static PlcResult run(OpcUaClient client, PlcOperation op) throws ToolControlException {
		List<PointInfo> points = op.points();
		List<NodeId> nodes = new ArrayList<>(points.size());
		try {
			for (PointInfo p : points) {
				nodes.add(nodeId(p));
			}
		} catch (IllegalArgumentException e) {
			throw ToolControlException.of(ToolControlException.INTERFACE_MISCONFIGURED, e.getMessage());
		}
		int chunk = (int) OpcUaSessions.longOf(op.settings(), "maxNodesPerRequest", MAX_NODES);
		return op.kind() == PlcOperation.Kind.READ ? read(client, points, nodes, chunk) : write(client, op, points, nodes, chunk);
	}

	private static PlcResult read(OpcUaClient client, List<PointInfo> points, List<NodeId> nodes, int chunk) throws ToolControlException {
		Map<String, Object> values = new LinkedHashMap<>();
		PlcStatus first = null;
		for (int from = 0; from < nodes.size(); from += chunk) {
			int to = Math.min(nodes.size(), from + chunk);
			List<DataValue> got;
			try {
				got = client.readValues(0.0, TimestampsToReturn.Neither, nodes.subList(from, to));
			} catch (UaException e) {
				throw failure(e);
			}
			for (int i = from; i < to; i++) {
				PointInfo p = points.get(i);
				DataValue dv = got.get(i - from);
				StatusCode sc = dv.getStatusCode() != null ? dv.getStatusCode() : StatusCode.GOOD;
				if (sc.isBad()) {
					first = first != null ? first : status(sc.getValue(), p.getName());
					continue;
				}
				try {
					values.put(p.getName(), fromOpcUa(p, dv.getValue() != null ? dv.getValue().getValue() : null));
				} catch (IllegalArgumentException e) {
					first = first != null ? first : new PlcStatus(PROTOCOL, StatusCodes.Bad_TypeMismatch, "Bad_TypeMismatch — " + e.getMessage());
				}
			}
		}
		return new PlcResult(values, first != null ? first : PlcStatus.ok(PROTOCOL));
	}

	private static PlcResult write(OpcUaClient client, PlcOperation op, List<PointInfo> points, List<NodeId> nodes, int chunk)
			throws ToolControlException {
		List<DataValue> data = new ArrayList<>(points.size());
		for (PointInfo p : points) {
			Object value = op.values() != null ? op.values().get(p.getName()) : null;
			if (value == null) {
				throw ToolControlException.of(ToolControlException.INVALID_REQUEST, "쓰기 값이 없습니다: " + p.getName());
			}
			try {
				data.add(DataValue.valueOnly(new Variant(toOpcUa(p, value))));
			} catch (IllegalArgumentException e) {
				throw ToolControlException.of(ToolControlException.INVALID_REQUEST, "쓰기 값이 잘못되었습니다: " + e.getMessage());
			}
		}
		for (int from = 0; from < nodes.size(); from += chunk) {
			int to = Math.min(nodes.size(), from + chunk);
			List<WriteValue> writes = new ArrayList<>();
			for (int i = from; i < to; i++) {
				writes.add(new WriteValue(nodes.get(i), AttributeId.Value.uid(), null, data.get(i)));
			}
			List<StatusCode> results;
			try {
				StatusCode[] r = client.write(writes).getResults();
				results = r != null ? List.of(r) : List.of();
			} catch (UaException e) {
				throw failure(e);
			}
			for (int i = 0; i < results.size(); i++) {
				if (results.get(i).isBad()) {
					return new PlcResult(Map.of(), status(results.get(i).getValue(), points.get(from + i).getName()));
				}
			}
		}
		return new PlcResult(Map.of(), PlcStatus.ok(PROTOCOL));
	}

	/** 서비스 호출 실패 — 시간 초과는 504, 나머지는 503 */
	static ToolControlException failure(UaException e) {
		long code = e.getStatusCode() != null ? e.getStatusCode().getValue() : 0;
		boolean timeout = code == StatusCodes.Bad_Timeout || code == StatusCodes.Bad_RequestTimeout;
		return ToolControlException.of(timeout ? ToolControlException.PLC_TIMEOUT : ToolControlException.PLC_UNAVAILABLE,
			"OPC UA 서비스 호출 실패: " + name(code) + " — " + e.getMessage());
	}

	public static PlcStatus status(long code, String point) {
		return new PlcStatus(PROTOCOL, code, name(code) + (point != null ? " (" + point + ")" : ""));
	}

	public static String name(long code) {
		return StatusCodes.lookup(code).map(s -> s[0]).orElse(String.format("0x%08X", code));
	}

	// ── 값 변환(O4) ──

	/** 서버 값 → 응답 값. 배열은 리스트, 숫자는 정의한 형으로(범위 밖이면 오류) */
	public static Object fromOpcUa(PointInfo p, Object v) {
		if (v != null && v.getClass().isArray()) {
			int n = Array.getLength(v);
			List<Object> out = new ArrayList<>(n);
			for (int i = 0; i < n; i++) {
				out.add(one(p, Array.get(v, i)));
			}
			return p.getCount() <= 1 && out.size() == 1 ? out.get(0) : out;
		}
		Object one = one(p, v);
		return p.getCount() > 1 ? List.of(one) : one;
	}

	private static Object one(PointInfo p, Object v) {
		if (v == null) {
			return null;
		}
		return switch (p.getType()) {
			case BOOL -> v instanceof Boolean b ? b : com.wow.be.tc.plc.protocol.ValueCodec.bool(plain(v), p.getName());
			case STRING -> v instanceof ByteString bs ? HexFormat.of().withUpperCase().formatHex(bs.bytesOrEmpty()) : String.valueOf(plain(v));
			case BYTES -> v instanceof ByteString bs ? HexFormat.of().withUpperCase().formatHex(bs.bytesOrEmpty()) : String.valueOf(v);
			case INT16 -> (int) integer(p, v, Short.MIN_VALUE, Short.MAX_VALUE);
			case UINT16 -> (int) integer(p, v, 0, 0xFFFF);
			case INT32 -> (int) integer(p, v, Integer.MIN_VALUE, Integer.MAX_VALUE);
			case UINT32 -> integer(p, v, 0, 0xFFFFFFFFL);
			case INT64 -> integer(p, v, Long.MIN_VALUE, Long.MAX_VALUE);
			case FLOAT32 -> number(p, v).floatValue();
			case FLOAT64 -> number(p, v).doubleValue();
		};
	}

	/** Milo 의 부호 없는 형 → 자바 숫자 */
	private static Object plain(Object v) {
		if (v instanceof UByte u) {
			return u.intValue();
		}
		if (v instanceof UShort u) {
			return u.intValue();
		}
		if (v instanceof UInteger u) {
			return u.longValue();
		}
		if (v instanceof ULong u) {
			return u.toBigInteger();
		}
		return v;
	}

	private static BigDecimal number(PointInfo p, Object v) {
		Object x = plain(v);
		if (x instanceof Number n) {
			return new BigDecimal(n.toString());
		}
		if (x instanceof Boolean b) {
			return b ? BigDecimal.ONE : BigDecimal.ZERO;
		}
		throw new IllegalArgumentException(p.getName() + ": 숫자가 아닌 값(" + v.getClass().getSimpleName() + ")");
	}

	private static long integer(PointInfo p, Object v, long min, long max) {
		BigDecimal d = number(p, v);
		long n;
		try {
			n = d.longValueExact();
		} catch (ArithmeticException e) {
			throw new IllegalArgumentException(p.getName() + ": 정수가 아닌 값 " + d);
		}
		if (n < min || n > max) {
			throw new IllegalArgumentException(p.getName() + ": " + n + " 는 " + p.getType() + " 범위 밖");
		}
		return n;
	}

	/** 쓰기 값 → 서버 형(배열이면 그 형의 배열) */
	static Object toOpcUa(PointInfo p, Object value) {
		if (p.getCount() > 1) {
			if (!(value instanceof List<?> list) || list.size() != p.getCount()) {
				throw new IllegalArgumentException(p.getName() + ": 값 " + p.getCount() + "개짜리 배열이어야 합니다");
			}
			Object first = writeOne(p, list.get(0), p.getName() + "[0]");
			Object arr = Array.newInstance(first.getClass(), list.size());
			for (int i = 0; i < list.size(); i++) {
				Array.set(arr, i, i == 0 ? first : writeOne(p, list.get(i), p.getName() + "[" + i + "]"));
			}
			return arr;
		}
		if (value instanceof List<?>) {
			throw new IllegalArgumentException(p.getName() + ": 배열이 아니라 값 하나여야 합니다");
		}
		return writeOne(p, value, p.getName());
	}

	private static Object writeOne(PointInfo p, Object v, String name) {
		PointInfo q = new PointInfo();
		q.setName(name);
		q.setType(p.getType());
		return switch (p.getType()) {
			case BOOL -> com.wow.be.tc.plc.protocol.ValueCodec.bool(v, name);
			case STRING -> String.valueOf(v);
			case BYTES -> {
				try {
					yield ByteString.of(HexFormat.of().parseHex(String.valueOf(v)));
				} catch (IllegalArgumentException e) {
					throw new IllegalArgumentException(name + ": 16진 문자열이어야 합니다 — " + v);
				}
			}
			case INT16 -> (short) integer(q, text(v), Short.MIN_VALUE, Short.MAX_VALUE);
			case UINT16 -> ushort((int) integer(q, text(v), 0, 0xFFFF));
			case INT32 -> (int) integer(q, text(v), Integer.MIN_VALUE, Integer.MAX_VALUE);
			case UINT32 -> uint(integer(q, text(v), 0, 0xFFFFFFFFL));
			case INT64 -> integer(q, text(v), Long.MIN_VALUE, Long.MAX_VALUE);
			case FLOAT32 -> number(q, text(v)).floatValue();
			case FLOAT64 -> number(q, text(v)).doubleValue();
		};
	}

	/** 숫자 문자열("25")도 받는다 */
	private static Object text(Object v) {
		if (v instanceof String s) {
			try {
				return new BigDecimal(s.trim());
			} catch (NumberFormatException e) {
				return s;
			}
		}
		return v;
	}

	// ── 미리보기(O7) ──

	@Override
	public List<PlannedRequest> plan(PlcOperation op) {
		for (PointInfo p : op.points()) {
			validatePoint(p, op.kind() == PlcOperation.Kind.WRITE ? InterfaceType.WRITE : InterfaceType.READ);
			if (op.kind() == PlcOperation.Kind.WRITE) {
				toOpcUa(p, op.values() != null ? op.values().get(p.getName()) : null);
			}
		}
		int chunk = (int) OpcUaSessions.longOf(op.settings(), "maxNodesPerRequest", MAX_NODES);
		List<PlannedRequest> out = new ArrayList<>();
		List<PointInfo> points = op.points();
		for (int from = 0; from < points.size(); from += chunk) {
			List<PointInfo> part = points.subList(from, Math.min(points.size(), from + chunk));
			String target = part.get(0).getAddress().trim() + (part.size() > 1 ? " 외 " + (part.size() - 1) : "");
			out.add(new PlannedRequest(out.size() + 1, op.kind() == PlcOperation.Kind.READ ? "Read(Value)" : "Write(Value)", target,
				part.size(), "노드", part.stream().map(PointInfo::getName).toList(), ""));
		}
		return out;
	}

	/** TCP 프레임 규약이 없다 — TCP 어댑터·연결 시험이 부르지 않는다 */
	@Override
	public WireProtocol wire(Map<String, Object> settings) {
		return null;
	}

	@Override
	public ProtocolDescriptor describe() {
		List<PointType> all = List.of(PointType.values());
		return new ProtocolDescriptor(PROTOCOL, "OPC UA", List.of(
			new ProtocolDescriptor.Setting("endpointPath", "TEXT", "", null, null, null, false,
				"엔드포인트 경로 — opc.tcp://주소:포트 뒤에 붙는다(예 /milo). 포트는 커넥터 포트 첫 번째(보통 4840)"),
			new ProtocolDescriptor.Setting("requestTimeoutMs", "NUMBER", (int) DEFAULT_REQUEST_TIMEOUT_MS, 100, 600_000, null, false, "서비스 호출 시간 초과(ms)"),
			new ProtocolDescriptor.Setting("sessionTimeoutMs", "NUMBER", (int) DEFAULT_SESSION_TIMEOUT_MS, 10_000, 3_600_000, null, false, "세션 시간 초과(ms)"),
			new ProtocolDescriptor.Setting("maxNodesPerRequest", "NUMBER", MAX_NODES, 1, 10_000, null, false, "Read·Write 한 번의 최대 노드 수"),
			new ProtocolDescriptor.Setting("publishingIntervalMs", "NUMBER", 1000, 50, 3_600_000, null, false, "구독 발행 주기(ms)"),
			new ProtocolDescriptor.Setting("samplingIntervalMs", "NUMBER", 500, 0, 3_600_000, null, false, "구독 샘플링 주기(ms)")),
			// 인터페이스 settings — 읽기 방식(구독), 구독 주기·데드밴드, 한도(docs/opcua-design.md §4)
			List.of(
				new ProtocolDescriptor.Setting("accessMode", "ENUM", ACCESS_READ, null, null, List.of(ACCESS_READ, ACCESS_SUBSCRIBE), false,
					"SUBSCRIBE 면 값이 바뀔 때마다 서버가 알려 주고, 그 값을 전달 정의(WebHook·DB 표)로 보낸다(READ 인터페이스만)"),
				new ProtocolDescriptor.Setting("publishingIntervalMs", "NUMBER", null, 50, 3_600_000, null, false, "구독 발행 주기(ms)"),
				new ProtocolDescriptor.Setting("samplingIntervalMs", "NUMBER", null, 0, 3_600_000, null, false, "구독 샘플링 주기(ms)"),
				new ProtocolDescriptor.Setting("deadband", "NUMBER", null, 0, 1_000_000_000, null, false,
					"절대값 데드밴드 — 이보다 작게 바뀌면 알리지 않는다(숫자 점)"),
				new ProtocolDescriptor.Setting("maxNodesPerRequest", "NUMBER", null, 1, 10_000, null, false, "Read·Write 한 번의 최대 노드 수")),
			List.of(), List.of("ns=2;s=Line1.Temp", "ns=2;i=1001", "i=2258"));
	}
}
