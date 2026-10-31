package com.wow.be.tc.interfs;

import com.wow.be.tc.plc.protocol.ByteOrder;
import com.wow.be.tc.plc.protocol.PointType;

import lombok.Data;

/**
 * 인터페이스의 점(point) — 이름 하나에 값 하나(또는 배열) (docs/plc-protocol-design.md §4.2).
 * 주소는 프로토콜별 문자열이고 커넥터의 드라이버가 검증한다(MELSEC {@code D100}, Modbus {@code HR40001}, OPC UA {@code ns=2;s=…}).
 */
@Data
public class PointInfo {

	/** 요청·응답 data 의 키 */
	private String name;

	/** 인터페이스 안의 순서(0부터) */
	private int order;

	private String address;

	private PointType type;

	/** 배열 길이(기본 1). 1 보다 크면 값이 JSON 배열이다 */
	private int count = 1;

	/** STRING·BYTES 의 바이트 수 */
	private int length;

	/** 커넥터 기본값을 이 점만 바꿀 때 */
	private ByteOrder byteOrder;

	private ByteOrder wordOrder;

	/** 배율 — 공학 값 = 원시 값 × scale + offset (숫자형만, docs/interface-detail-design.md §9.2). 비면 1 */
	private Double scale;

	/** 오프셋 — 비면 0 */
	private Double offset;

	/** 단위(표시용, 예 ℃) — 응답에는 싣지 않는다 */
	private String unit;

	/** 쓰기 값 범위(공학 값) — 벗어나면 400 */
	private Double min;

	private Double max;

	private String description;
}
