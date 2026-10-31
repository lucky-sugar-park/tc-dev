package com.wow.be.tc.plc.protocol;

/**
 * 점(point) 값의 형 (docs/plc-protocol-design.md §4.2). 바이트 수는 한 개 기준 — {@code STRING}·{@code BYTES} 는 점의 {@code length}.
 */
public enum PointType {

	BOOL(0),
	INT16(2),
	UINT16(2),
	INT32(4),
	UINT32(4),
	INT64(8),
	FLOAT32(4),
	FLOAT64(8),
	STRING(-1),
	BYTES(-1);

	private final int bytes;

	PointType(int bytes) {
		this.bytes = bytes;
	}

	/** 값 하나의 바이트 수. BOOL 은 0(비트), STRING·BYTES 는 length */
	public int bytes(int length) {
		return bytes >= 0 ? bytes : length;
	}

	public boolean hasLength() {
		return bytes < 0;
	}
}
