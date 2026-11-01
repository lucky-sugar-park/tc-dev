package com.wow.be.tc.vplc.core.memory;

import java.util.List;

import com.wow.be.tc.vplc.VirtualPlcException;

/**
 * 영역 메모리 능력 — "영역 + 번호 + 값"(MELSEC·Modbus, docs/vplc-architecture.md §2.3). 게시된 가상 PLC 가 구현한다(값은 메모리).
 * 값은 영역 단위 그대로 — WORD 는 0~65535(쓰기는 -32768~65535), BIT 는 0·1.
 */
public interface AreaMemory {

	enum Unit { WORD, BIT }

	/**
	 * 영역 하나
	 *
	 * @param code 영역 코드(MELSEC 메모리 종류 {@code D*}, Modbus {@code hr})
	 * @param label 화면 표시({@code D}, {@code HR})
	 * @param size 칸 수(단위 기준)
	 * @param hexAddress 주소를 16진으로 적는가(MELSEC W·B·X·Y…)
	 * @param bitDevice 워드로 담지만 비트마다 주소가 있는가(MELSEC M·X…) — 화면이 비트마다 주소를 붙인다
	 */
	record Area(String code, String label, Unit unit, int size, boolean hexAddress, boolean bitDevice) {
	}

	List<Area> areas();

	int[] read(String area, int start, int count) throws VirtualPlcException;

	void write(String area, int start, int[] values) throws VirtualPlcException;

	/** 영역 전체를 0 으로 */
	void clear(String area) throws VirtualPlcException;
}
