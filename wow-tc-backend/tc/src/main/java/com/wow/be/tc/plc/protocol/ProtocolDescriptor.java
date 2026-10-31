package com.wow.be.tc.plc.protocol;

import java.util.List;

/**
 * 프로토콜 설명 — 화면이 커넥터 설정 폼과 점 주소 도움말을 그리는 데 쓴다(docs/plc-protocol-design.md §16).
 * 검증은 여전히 드라이버가 한다 — 이것은 안내다.
 *
 * @param settings   커넥터 {@code settings} 항목
 * @param areas      주소 영역(MELSEC 디바이스, Modbus 영역)
 * @param examples   주소 예
 * @param rawSupported RAW 인터페이스(헤더·데이터 바이트 정의)를 받는가
 */
public record ProtocolDescriptor(String protocol, String label, List<Setting> settings, List<Area> areas, List<String> examples,
		boolean rawSupported) {

	/**
	 * @param type    NUMBER · ENUM · TEXT
	 * @param options ENUM 이면 고를 값
	 * @param hex     16진 입력(0x…)도 받는가
	 */
	public record Setting(String key, String type, Object defaultValue, Integer min, Integer max, List<String> options, boolean hex,
			String description) {
	}

	/**
	 * @param prefix     주소 앞부분(D, M, HR …)
	 * @param bit        비트 영역이면 BOOL 만
	 * @param hexNumber  번호를 16진으로 쓰는가(MELSEC X·Y·B·W …)
	 * @param readOnly   읽기만 되는가(Modbus IR·DI)
	 * @param types      쓸 수 있는 점 형
	 */
	public record Area(String prefix, String label, boolean bit, boolean hexNumber, boolean readOnly, List<PointType> types) {
	}
}
