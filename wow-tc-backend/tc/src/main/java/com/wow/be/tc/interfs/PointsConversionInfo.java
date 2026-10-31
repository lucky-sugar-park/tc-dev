package com.wow.be.tc.interfs;

import java.util.List;

/**
 * RAW → POINTS 변환 결과 (docs/plc-protocol-design.md §8·§15).
 *
 * @param applied      바꿨는가(미리보기면 false)
 * @param points       만든 점 — 이름은 옛 데이터 속성 이름 그대로
 * @param warnings     값 표현이 달라지는 것 등 — 적용 전에 외부 시스템과 확인할 것
 * @param replyName    옛 응답 인터페이스 — POINTS 는 쓰지 않는다. 지우는 것은 운영자가
 * @param replyUsedBy  그 응답 인터페이스를 아직 쓰는 다른 인터페이스(바꾼 이 인터페이스는 빼고)
 */
public record PointsConversionInfo(String name, boolean dryRun, boolean applied, List<PointInfo> points, List<String> warnings,
		String replyName, List<String> replyUsedBy) {
}
