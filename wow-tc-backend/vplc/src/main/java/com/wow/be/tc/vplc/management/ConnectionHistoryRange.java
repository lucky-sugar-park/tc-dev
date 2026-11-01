package com.wow.be.tc.vplc.management;

import java.util.List;

/**
 * 연결 이력 구간 결과 — tc 의 RangeQueries.Result 와 같은 모양(docs/range-grid-design.md §4). total 은 셌을 때만.
 */
public record ConnectionHistoryRange(List<ConnectionClientHistoryInfo> rows, Long total, long asOf, int start) {

	public static final int MAX_LIMIT = 1000;

	/** 구간 — 검색 결과 안의 절대 위치(0부터)·행 수·스냅샷 기준·건수를 셀지 */
	public record Params(Integer start, Integer limit, Long asOf, Boolean withTotal) {

		public int startOrZero() {
			return start == null || start < 0 ? 0 : start;
		}

		public int limitClamped() {
			return limit == null || limit < 1 ? 100 : Math.min(limit, MAX_LIMIT);
		}
	}

	/** 요청 본문 — 화면 검색 조건(page·size 는 쓰지 않음)과 구간 */
	public record Request(ConnectionHistoryQuery filter, Params range) {
	}
}
