package com.wow.be.tc.plc.protocol;

import com.wow.be.tc.Types.InterfaceType;
import com.wow.be.tc.interfs.PointInfo;

/**
 * 워드 안 비트 점(MELSEC {@code D100.A}, Modbus {@code HR1.15}) 검증 — 프로토콜 공통 (docs/word-bit-points.md).
 * BOOL·개수 1·순서 재정의 없음, 쓰기 인터페이스에는 안 된다(읽고-바꾸고-쓰기 사이에 PLC 가 바꾼 다른 비트를 덮어쓴다).
 */
public final class WordBits {

	private WordBits() {
	}

	/** @throws IllegalArgumentException 규칙에 맞지 않으면(400) */
	public static void validate(PointInfo p, Object address, InterfaceType interfaceType) {
		if (p.getType() != PointType.BOOL) {
			throw new IllegalArgumentException(p.getName() + ": 워드 안 비트(" + address + ")는 BOOL 입니다");
		}
		if (p.getCount() != 1) {
			throw new IllegalArgumentException(p.getName() + ": 워드 안 비트(" + address + ")는 개수 1 입니다 — 여러 비트는 점을 여러 개로");
		}
		if (p.getByteOrder() != null || p.getWordOrder() != null) {
			throw new IllegalArgumentException(p.getName() + ": 워드 안 비트에는 바이트·워드 순서를 쓰지 않습니다");
		}
		if (interfaceType == InterfaceType.WRITE) {
			throw new IllegalArgumentException(p.getName() + ": 워드 안 비트(" + address
				+ ")는 읽기만 됩니다 — 쓰려면 비트 디바이스(MELSEC M …, Modbus 코일)나 tc 전용 워드를 쓰세요");
		}
	}
}
