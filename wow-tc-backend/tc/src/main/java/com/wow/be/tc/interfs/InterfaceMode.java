package com.wow.be.tc.interfs;

/**
 * 인터페이스 정의 방식 (docs/plc-protocol-design.md §4.2, 결정 P1).
 * <ul>
 *   <li>{@code POINTS} — 점(이름·주소·형) 목록. 프레임은 프로토콜 드라이버가 만든다. 응답 인터페이스가 필요 없다.</li>
 *   <li>{@code RAW} — 지금까지의 헤더·데이터 바이트 속성(MC 3E 프레임 그대로) + 응답 인터페이스. MELSEC 만, 호환용.</li>
 * </ul>
 */
public enum InterfaceMode {
	RAW, POINTS
}
