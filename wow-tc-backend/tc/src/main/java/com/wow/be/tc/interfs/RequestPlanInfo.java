package com.wow.be.tc.interfs;

import java.util.List;
import java.util.Map;

import com.wow.be.tc.plc.protocol.PlannedRequest;

/**
 * 요청 미리보기 결과 (docs/interface-detail-design.md §7.2) — 이 정의로 PLC 에 보낼 요청들. 보내지 않는다.
 *
 * @param settings 드라이버가 정규화한 유효 설정(지금은 커넥터 설정)
 * @param notes    읽는 사람이 알아야 할 것(채운 쓰기 값, 보낼 때 채우는 칸 등)
 */
public record RequestPlanInfo(String protocol, String plcName, Map<String, Object> settings, List<PlannedRequest> requests, List<String> notes) {

	/** 미리보기 요청 본문 — 인터페이스 정의(저장 전 폼 그대로도 된다)와 선택 쓰기 값 */
	public record Query(InterfaceInfo interfaceInfo, Map<String, Object> data) {
	}
}
