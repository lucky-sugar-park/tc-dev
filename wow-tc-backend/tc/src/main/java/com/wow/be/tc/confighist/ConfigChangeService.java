package com.wow.be.tc.confighist;

import java.util.Date;
import java.util.List;

import org.springframework.data.domain.Page;

import com.wow.be.tc.confighist.ConfigChangeInfo.TargetType;
import com.wow.be.tc.utils.RangeQueries;

/**
 * 설정 변경 이력 조회와 보존 정리 (docs/auth-and-audit-design.md §5.3, §5.4).
 */
public interface ConfigChangeService {

	/**
	 * 검색. 조건은 모두 선택이다. 최신순.
	 *
	 * @param page 0부터
	 */
	Page<ConfigChangeInfo> search(Date from, Date to, TargetType targetType, String targetId, String actorId, int page, int size);

	/** 구간 — 화면의 구간 스크롤 표(docs/range-grid-design.md). 최신순, 스냅샷은 시각(CHANGED_AT, 밀리초) */
	RangeQueries.Result<ConfigChangeInfo> range(Date from, Date to, TargetType targetType, String targetId, String actorId,
		RangeQueries.Params range);

	/** 한 대상의 이력 전체(최신순) — 상세 화면의 "변경 이력" 탭 */
	List<ConfigChangeInfo> historyOf(TargetType targetType, String targetId);

	/** 보존 기간이 지난 이력을 지운다. 지운 건수 */
	long purgeOlderThan(Date before);
}
