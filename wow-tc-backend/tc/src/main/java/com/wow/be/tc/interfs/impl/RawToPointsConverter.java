package com.wow.be.tc.interfs.impl;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import com.wow.be.tc.Types.InterfaceType;
import com.wow.be.tc.Types.PropType;
import com.wow.be.tc.interfs.InterfaceInfo;
import com.wow.be.tc.interfs.PointInfo;
import com.wow.be.tc.interfs.PropInfo;
import com.wow.be.tc.plc.protocol.PointType;
import com.wow.be.tc.plc.protocol.melsec.MelsecDevice;

/**
 * RAW(MC 3E 헤더·데이터 바이트 정의) 인터페이스 → POINTS 점 목록 (docs/plc-protocol-design.md §8·§15).
 *
 * <p>헤더 속성의 바이트를 순서대로 이어 3E BINARY 요청 머리로 읽는다 — 속성 이름이 아니라 바이트 자리로 본다(현장마다 이름이 달라도 된다).
 * 명령(0401 읽기·1401 쓰기, 워드 단위), 디바이스 코드, 시작 번호, 워드 수를 얻고, 데이터 속성(읽기는 응답 인터페이스, 쓰기는 이 인터페이스)을
 * 차례로 워드 자리에 놓아 점을 만든다. 점 이름은 데이터 속성 이름 그대로 — 요청·응답 {@code data} 의 키가 바뀌지 않는다.
 *
 * <p>알아볼 수 없으면 {@link IllegalArgumentException}(사유, 400) — 바꾸지 않는다. 값 표현이 달라지는 것은 경고로 돌려준다.
 */
final class RawToPointsConverter {

	/** 3E BINARY 요청 머리 — 서브헤더(2)·네트워크(1)·PC(1)·I/O(2)·국(1)·길이(2)·타이머(2)·명령(2)·서브 명령(2)·시작(3)·디바이스(1)·수(2) */
	static final int HEAD = 21;

	private RawToPointsConverter() {
	}

	/** @param points 만든 점 @param warnings 값 표현이 달라지는 것 등 */
	record Result(List<PointInfo> points, List<String> warnings) {
	}

	/**
	 * @param iface     바꿀 인터페이스(RAW, READ·WRITE)
	 * @param reply     읽기면 응답 인터페이스(데이터 속성), 쓰기면 null 이어도 된다
	 * @param settings  커넥터의 정규화한 MELSEC 설정 — 머리의 네트워크·PC·I/O·국 번호가 같아야 한다(POINTS 는 커넥터 설정으로 보낸다)
	 */
	static Result convert(InterfaceInfo iface, InterfaceInfo reply, Map<String, Object> settings) {
		if (iface.getInterfaceType() != InterfaceType.READ && iface.getInterfaceType() != InterfaceType.WRITE) {
			throw new IllegalArgumentException("READ·WRITE 인터페이스만 바꿉니다: " + iface.getInterfaceType());
		}
		byte[] h = header(iface.getHeaderProps());
		List<String> warnings = new ArrayList<>();
		if ((h[0] & 0xFF) != 0x50 || h[1] != 0x00) {
			throw new IllegalArgumentException("3E BINARY 요청 머리(서브헤더 5000)가 아닙니다: " + hex(h, 0, 2));
		}
		check(settings, "networkNo", h[2] & 0xFF, "네트워크 번호");
		check(settings, "pcNo", h[3] & 0xFF, "PC 번호");
		check(settings, "ioNo", le(h, 4, 2), "요구 I/O 번호");
		check(settings, "stationNo", h[6] & 0xFF, "요구 국 번호");
		int timer = le(h, 9, 2);
		if (settings.get("monitoringTimer") instanceof Number t && t.intValue() != timer) {
			warnings.add(String.format("감시 타이머가 커넥터 설정(0x%04X)으로 바뀝니다(지금 0x%04X)", t.intValue(), timer));
		}
		int command = le(h, 11, 2);
		int sub = le(h, 13, 2);
		boolean read = iface.getInterfaceType() == InterfaceType.READ;
		if (command != (read ? 0x0401 : 0x1401)) {
			throw new IllegalArgumentException(String.format("%s 인터페이스의 명령이 %s 가 아닙니다: 0x%04X", iface.getInterfaceType(),
				read ? "0401(일괄 읽기)" : "1401(일괄 쓰기)", command));
		}
		if (sub != 0x0000) {
			throw new IllegalArgumentException(String.format("워드 단위(서브 명령 0000)만 바꿉니다: 0x%04X", sub));
		}
		int start = le(h, 15, 3);
		MelsecDevice device = device(h[18]);
		int words = le(h, 19, 2);
		if (words < 1) {
			throw new IllegalArgumentException("워드 수가 0 입니다");
		}

		InterfaceInfo source = read ? reply : iface;
		if (source == null) {
			throw new IllegalArgumentException("응답 인터페이스(" + iface.getReplyName() + ")가 없어 데이터 정의를 알 수 없습니다");
		}
		List<PropInfo> props = new ArrayList<>(source.getDataProps() != null ? source.getDataProps() : List.of());
		if (props.isEmpty()) {
			throw new IllegalArgumentException((read ? "응답 인터페이스 " + source.getName() : "인터페이스") + " 에 데이터 속성이 없습니다");
		}
		props.sort(Comparator.comparingInt(PropInfo::getOrder));
		List<PointInfo> points = new ArrayList<>();
		int offset = 0;
		for (PropInfo p : props) {
			if (offset % 2 != 0) {
				throw new IllegalArgumentException(p.getName() + ": 워드 경계(짝수 바이트)에서 시작하지 않습니다(앞 속성들이 " + offset + "바이트)");
			}
			PointInfo point = new PointInfo();
			point.setName(p.getName());
			point.setAddress(device.name() + (device.isHexNumber() ? Integer.toHexString(start + offset / 2).toUpperCase() : String.valueOf(start + offset / 2)));
			point.setType(type(p, device, warnings));
			point.setCount(1);
			if (point.getType().hasLength()) {
				point.setLength(p.getLength());
			}
			point.setDescription(p.getDescription());
			points.add(point);
			offset += p.getLength();
		}
		if (offset != words * 2) {
			throw new IllegalArgumentException("데이터 속성 길이 합(" + offset + "바이트)이 머리의 워드 수(" + words + "워드 = " + words * 2 + "바이트)와 다릅니다");
		}
		return new Result(points, warnings);
	}

	private static PointType type(PropInfo p, MelsecDevice device, List<String> warnings) {
		PropType t = p.getType();
		int len = p.getLength();
		if (device.isBit()) {
			throw new IllegalArgumentException("비트 디바이스(" + device + ")를 워드 단위로 읽는 정의는 바꾸지 않습니다 — 점을 BOOL 로 직접 정의하세요");
		}
		if (t == null) {
			throw new IllegalArgumentException(p.getName() + ": 형이 없습니다");
		}
		switch (t) {
			case SHORT:
				if (len == 2) {
					return PointType.INT16;
				}
				break;
			case INT:
				if (len == 4) {
					return PointType.INT32;
				}
				break;
			case LONG:
				if (len == 8) {
					return PointType.INT64;
				}
				break;
			case DOUBLE:
				if (len == 4) {
					return PointType.FLOAT32;
				}
				break;
			case STRING:
				warnings.add(p.getName() + ": STRING — 읽은 값 끝의 NUL 을 빼고, 쓰기 값이 정의 길이보다 짧아도 된다(RAW 는 길이가 꼭 같아야 했다)");
				return PointType.STRING;
			case BYTE:
				warnings.add(p.getName() + ": BYTE — 값이 Base64 문자열에서 16진 문자열로 바뀐다(외부 시스템 확인 필요)");
				return PointType.BYTES;
			case BOOLEAN:
			default:
				break;
		}
		throw new IllegalArgumentException(p.getName() + ": " + t + "(" + len + "바이트)는 점 형으로 바꿀 수 없습니다"
			+ (t == PropType.BOOLEAN ? " — 워드의 한 바이트를 BOOLEAN 으로 읽는 정의는 비트 디바이스 BOOL 점으로 직접 바꾸세요" : ""));
	}

	private static MelsecDevice device(byte code) {
		for (MelsecDevice d : MelsecDevice.values()) {
			if (d.binaryCode() == code) {
				return d;
			}
		}
		throw new IllegalArgumentException(String.format("모르는 디바이스 코드입니다: 0x%02X", code & 0xFF));
	}

	/** 헤더 속성 바이트를 순서대로 잇는다 — BYTE 형(16진)만 */
	static byte[] header(List<PropInfo> props) {
		if (props == null || props.isEmpty()) {
			throw new IllegalArgumentException("헤더 속성이 없습니다");
		}
		List<PropInfo> sorted = new ArrayList<>(props);
		sorted.sort(Comparator.comparingInt(PropInfo::getOrder));
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (PropInfo p : sorted) {
			if (p.getType() != PropType.BYTE) {
				throw new IllegalArgumentException(p.getName() + ": 헤더 속성이 BYTE(16진)가 아닙니다(" + p.getType() + ") — ASCII 프레임 정의는 바꾸지 않습니다");
			}
			byte[] b = p.getValue() instanceof byte[] v ? v : p.getStrValue() != null ? HexFormat.of().parseHex(p.getStrValue()) : new byte[0];
			out.writeBytes(b);
		}
		byte[] h = out.toByteArray();
		if (h.length != HEAD) {
			throw new IllegalArgumentException("헤더가 " + h.length + "바이트 — 3E BINARY 일괄 읽기·쓰기 머리는 " + HEAD + "바이트입니다");
		}
		return h;
	}

	private static void check(Map<String, Object> settings, String key, int actual, String what) {
		if (settings.get(key) instanceof Number n && n.intValue() != actual) {
			throw new IllegalArgumentException(String.format("%s가 커넥터 설정과 다릅니다(헤더 0x%X, 커넥터 %s 0x%X) — POINTS 는 커넥터 설정으로 보내므로 먼저 맞추세요",
				what, actual, key, n.intValue()));
		}
	}

	private static int le(byte[] b, int at, int n) {
		int v = 0;
		for (int i = n - 1; i >= 0; i--) {
			v = v << 8 | (b[at + i] & 0xFF);
		}
		return v;
	}

	private static String hex(byte[] b, int at, int n) {
		return HexFormat.of().withUpperCase().formatHex(b, at, at + n);
	}
}
