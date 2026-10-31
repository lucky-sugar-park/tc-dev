package com.wow.be.tc.plc.protocol;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * 점 값 ↔ 레지스터 영역 바이트 (docs/plc-protocol-design.md §5). 프로토콜을 모른다 — 16비트 워드 안의 바이트 순서와 32·64비트
 * 값의 워드 순서만 안다. 바이트 배열은 전선에 실리는 순서 그대로다(워드마다 2바이트).
 *
 * <ul>
 *   <li>정수·실수는 워드 단위로 조립한다. MELSEC(바이트 LITTLE·워드 LITTLE)이면 값 전체가 리틀엔디안이다.</li>
 *   <li>STRING·BYTES 는 영역 바이트를 그대로 쓴다(문자 순서 = 전선 순서). STRING 은 UTF-8, 끝의 NUL 은 뺀다.</li>
 *   <li>BOOL 은 비트 단위라 드라이버가 다룬다.</li>
 * </ul>
 * 잘못된 쓰기 값은 {@link IllegalArgumentException}(요청 오류 400).
 */
public final class ValueCodec {

	private ValueCodec() {
	}

	/** 값 하나(배열이면 count 개)가 차지하는 바이트 수 — 홀수 길이 문자열도 워드에 맞춰 짝수로 */
	public static int byteSpan(PointType type, int count, int length) {
		int one = type.bytes(length);
		return ((one * Math.max(count, 1)) + 1) / 2 * 2;
	}

	public static Object decode(PointType type, int count, int length, byte[] area, int offset, ByteOrder byteOrder, ByteOrder wordOrder) {
		if (type.hasLength()) {
			byte[] raw = new byte[length * Math.max(count, 1)];
			System.arraycopy(area, offset, raw, 0, raw.length);
			if (type == PointType.BYTES) {
				return HexFormat.of().withUpperCase().formatHex(raw);
			}
			int end = 0;
			while (end < raw.length && raw[end] != 0) {
				end++;
			}
			return new String(raw, 0, end, StandardCharsets.UTF_8);
		}
		int size = type.bytes(length);
		if (count <= 1) {
			return decodeOne(type, area, offset, size, byteOrder, wordOrder);
		}
		List<Object> values = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			values.add(decodeOne(type, area, offset + i * size, size, byteOrder, wordOrder));
		}
		return values;
	}

	public static byte[] encode(PointType type, int count, int length, Object value, ByteOrder byteOrder, ByteOrder wordOrder, String name) {
		byte[] out = new byte[byteSpan(type, count, length)];
		if (type.hasLength()) {
			byte[] raw = type == PointType.BYTES ? hex(value, name) : text(value, name).getBytes(StandardCharsets.UTF_8);
			if (raw.length > length * Math.max(count, 1)) {
				throw new IllegalArgumentException(name + ": 길이 " + raw.length + "바이트가 정의(" + length + ")보다 깁니다");
			}
			System.arraycopy(raw, 0, out, 0, raw.length);
			return out;
		}
		int size = type.bytes(length);
		if (count <= 1) {
			encodeOne(type, single(value, name), out, 0, size, byteOrder, wordOrder, name);
			return out;
		}
		if (!(value instanceof List<?> list) || list.size() != count) {
			throw new IllegalArgumentException(name + ": 값 " + count + "개짜리 배열이어야 합니다");
		}
		for (int i = 0; i < count; i++) {
			encodeOne(type, list.get(i), out, i * size, size, byteOrder, wordOrder, name + "[" + i + "]");
		}
		return out;
	}

	// ── 숫자 ──

	private static Object decodeOne(PointType type, byte[] area, int offset, int size, ByteOrder byteOrder, ByteOrder wordOrder) {
		long bits = readBits(area, offset, size, byteOrder, wordOrder);
		return switch (type) {
			case INT16 -> (int) (short) bits;
			case UINT16 -> (int) (bits & 0xFFFFL);
			case INT32 -> (int) bits;
			case UINT32 -> bits & 0xFFFFFFFFL;
			case INT64 -> bits;
			case FLOAT32 -> Float.intBitsToFloat((int) bits);
			case FLOAT64 -> Double.longBitsToDouble(bits);
			default -> throw new IllegalStateException("숫자 형이 아닙니다: " + type);
		};
	}

	private static void encodeOne(PointType type, Object value, byte[] out, int offset, int size, ByteOrder byteOrder,
			ByteOrder wordOrder, String name) {
		long bits = switch (type) {
			case INT16 -> range(integer(value, name), Short.MIN_VALUE, Short.MAX_VALUE, name);
			case UINT16 -> range(integer(value, name), 0, 0xFFFF, name);
			case INT32 -> range(integer(value, name), Integer.MIN_VALUE, Integer.MAX_VALUE, name);
			case UINT32 -> range(integer(value, name), 0, 0xFFFFFFFFL, name);
			case INT64 -> integer(value, name);
			case FLOAT32 -> Float.floatToIntBits((float) real(value, name)) & 0xFFFFFFFFL;
			case FLOAT64 -> Double.doubleToLongBits(real(value, name));
			default -> throw new IllegalStateException("숫자 형이 아닙니다: " + type);
		};
		writeBits(bits, out, offset, size, byteOrder, wordOrder);
	}

	/** size 바이트(짝수)를 워드로 읽어 조립한다. wordOrder LITTLE 이면 첫 워드가 하위 */
	static long readBits(byte[] area, int offset, int size, ByteOrder byteOrder, ByteOrder wordOrder) {
		int words = size / 2;
		long bits = 0;
		for (int i = 0; i < words; i++) {
			int lo = area[offset + i * 2] & 0xFF;
			int hi = area[offset + i * 2 + 1] & 0xFF;
			long word = byteOrder == ByteOrder.LITTLE ? (hi << 8) | lo : (lo << 8) | hi;
			int shift = wordOrder == ByteOrder.LITTLE ? i * 16 : (words - 1 - i) * 16;
			bits |= word << shift;
		}
		return bits;
	}

	static void writeBits(long bits, byte[] out, int offset, int size, ByteOrder byteOrder, ByteOrder wordOrder) {
		int words = size / 2;
		for (int i = 0; i < words; i++) {
			int shift = wordOrder == ByteOrder.LITTLE ? i * 16 : (words - 1 - i) * 16;
			int word = (int) ((bits >>> shift) & 0xFFFF);
			byte first = (byte) (byteOrder == ByteOrder.LITTLE ? word : word >>> 8);
			byte second = (byte) (byteOrder == ByteOrder.LITTLE ? word >>> 8 : word);
			out[offset + i * 2] = first;
			out[offset + i * 2 + 1] = second;
		}
	}

	// ── 쓰기 값 해석 ──

	private static Object single(Object value, String name) {
		if (value instanceof List<?>) {
			throw new IllegalArgumentException(name + ": 배열이 아니라 값 하나여야 합니다");
		}
		return value;
	}

	private static long integer(Object value, String name) {
		try {
			if (value instanceof Number n) {
				BigDecimal d = new BigDecimal(n.toString());
				return d.longValueExact();
			}
			if (value instanceof String s && !s.isBlank()) {
				return new BigDecimal(s.trim()).longValueExact();
			}
		} catch (ArithmeticException | NumberFormatException e) {
			throw new IllegalArgumentException(name + ": 정수가 아닙니다 — " + value);
		}
		throw new IllegalArgumentException(name + ": 정수 값이 필요합니다 — " + value);
	}

	private static double real(Object value, String name) {
		if (value instanceof Number n) {
			return n.doubleValue();
		}
		if (value instanceof String s && !s.isBlank()) {
			try {
				return Double.parseDouble(s.trim());
			} catch (NumberFormatException e) {
				// 아래
			}
		}
		throw new IllegalArgumentException(name + ": 숫자 값이 필요합니다 — " + value);
	}

	private static long range(long v, long min, long max, String name) {
		if (v < min || v > max) {
			throw new IllegalArgumentException(name + ": " + v + " 는 범위(" + min + "~" + max + ") 밖입니다");
		}
		return v;
	}

	private static String text(Object value, String name) {
		if (value == null || value instanceof List<?>) {
			throw new IllegalArgumentException(name + ": 문자열 값이 필요합니다");
		}
		return value.toString();
	}

	private static byte[] hex(Object value, String name) {
		try {
			return HexFormat.of().parseHex(text(value, name));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(name + ": 16진 문자열이어야 합니다 — " + value);
		}
	}

	/** 16비트 워드 하나(offset 의 2바이트, byteOrder 로 조립) 안의 비트 — 워드 안 비트 점(docs/word-bit-points.md) */
	public static boolean bitOf(byte[] area, int offset, int bit, ByteOrder byteOrder) {
		long word = readBits(area, offset, 2, byteOrder, ByteOrder.LITTLE);
		return ((word >> bit) & 1) == 1;
	}

	/** BOOL 쓰기 값 — true/false, 1/0, "true"/"false"/"1"/"0" */
	public static boolean bool(Object value, String name) {
		if (value instanceof Boolean b) {
			return b;
		}
		if (value instanceof Number n && (n.intValue() == 0 || n.intValue() == 1)) {
			return n.intValue() == 1;
		}
		if (value instanceof String s) {
			switch (s.trim().toLowerCase()) {
				case "true", "1", "on" -> {
					return true;
				}
				case "false", "0", "off" -> {
					return false;
				}
				default -> {
				}
			}
		}
		throw new IllegalArgumentException(name + ": BOOL 값(true/false)이 필요합니다 — " + value);
	}
}
