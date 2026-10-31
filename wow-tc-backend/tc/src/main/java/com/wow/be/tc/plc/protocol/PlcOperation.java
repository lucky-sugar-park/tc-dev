package com.wow.be.tc.plc.protocol;

import java.util.List;
import java.util.Map;

import com.wow.be.tc.interfs.PointInfo;

/**
 * PLC 에 할 작업 하나 — 읽기(점들) 또는 쓰기(점들과 값) (docs/plc-protocol-design.md §3).
 * 커넥터의 프로토콜·설정을 함께 들고 다녀, 어느 인스턴스(로컬·원격 어댑터)에서든 같은 드라이버로 프레임을 만든다.
 *
 * @param settings 드라이버가 정규화한 커넥터 설정
 * @param values   쓰기 값(점 이름 → 값). 읽기면 빈 맵
 */
public record PlcOperation(String protocol, Map<String, Object> settings, Kind kind, List<PointInfo> points,
		Map<String, Object> values, String reqId) {

	public enum Kind {
		READ, WRITE
	}
}
