package com.wow.be.tc.vplc.opcua;

import java.math.BigDecimal;

import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UShort;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned;

/**
 * 태그 형 — tc 의 점 형과 같은 이름(docs/opcua-design.md O4). OPC UA 기본 데이터 형(ns=0)과 자바 값.
 */
public enum TagType {

	BOOL(1), INT16(4), UINT16(5), INT32(6), UINT32(7), INT64(8), FLOAT32(10), FLOAT64(11), STRING(12);

	private final int dataTypeId;

	TagType(int dataTypeId) {
		this.dataTypeId = dataTypeId;
	}

	public NodeId dataType() {
		return new NodeId(0, dataTypeId);
	}

	public boolean numeric() {
		return this != BOOL && this != STRING;
	}

	/** JSON 값(숫자·문자열·불) → 이 형의 OPC UA 값. 범위 밖이면 IllegalArgumentException */
	public Object of(Object v) {
		if (v == null) {
			throw new IllegalArgumentException("값이 없습니다");
		}
		return switch (this) {
			case BOOL -> v instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(v).trim()) || "1".equals(String.valueOf(v).trim());
			case STRING -> String.valueOf(v);
			case INT16 -> (short) integer(v, Short.MIN_VALUE, Short.MAX_VALUE);
			case UINT16 -> Unsigned.ushort((int) integer(v, 0, 0xFFFF));
			case INT32 -> (int) integer(v, Integer.MIN_VALUE, Integer.MAX_VALUE);
			case UINT32 -> Unsigned.uint(integer(v, 0, 0xFFFFFFFFL));
			case INT64 -> integer(v, Long.MIN_VALUE, Long.MAX_VALUE);
			case FLOAT32 -> decimal(v).floatValue();
			case FLOAT64 -> decimal(v).doubleValue();
		};
	}

	/** OPC UA 값 → JSON 값(부호 없는 형은 숫자로) */
	public static Object plain(Object v) {
		if (v instanceof UShort u) {
			return u.intValue();
		}
		if (v instanceof UInteger u) {
			return u.longValue();
		}
		return v;
	}

	private static BigDecimal decimal(Object v) {
		Object p = plain(v);
		try {
			return p instanceof Number n ? new BigDecimal(n.toString()) : new BigDecimal(String.valueOf(p).trim());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("숫자가 아닙니다: " + v);
		}
	}

	private static long integer(Object v, long min, long max) {
		BigDecimal d = decimal(v);
		long n = d.setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
		if (n < min || n > max) {
			throw new IllegalArgumentException(n + " 는 범위(" + min + "~" + max + ") 밖입니다");
		}
		return n;
	}
}
