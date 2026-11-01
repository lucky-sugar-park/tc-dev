package com.wow.be.tc.endpoints;

import java.util.Date;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wow.be.framework.security.authz.Requires;
import com.wow.be.tc.confighist.ConfigChangeInfo;
import com.wow.be.tc.confighist.ConfigChangeInfo.TargetType;
import com.wow.be.tc.confighist.ConfigChangeService;
import com.wow.be.tc.utils.RangeQueries;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 설정 변경 이력 조회 (docs/auth-and-audit-design.md §5.3).
 */
@Tag(name = "Config History", description = "누가 어떤 설정을 바꿨는지 — 설정 변경 이력 조회")
@RestController
@RequestMapping("/api/v1/config-history")
@RequiredArgsConstructor
@Requires("tc.confighist")
public class ConfigChangeHistoryController {

	private final ConfigChangeService configChangeService;

	/** 검색 조건. 모두 선택이다. */
	public record SearchRequest(Date from, Date to, TargetType targetType, String targetId, String actorId,
			Integer page, Integer size) {
	}

	/** 검색 결과 한 쪽 */
	public record PageResult(List<ConfigChangeInfo> items, long total, int page, int size) {
	}

	@Operation(summary = "설정 변경 이력 검색", description = "기간·대상 종류·대상 ID·행위자로 찾는다. 최신순, 쪽 크기 최대 500.")
	@Requires(action = 'R')
	@PostMapping("/search")
	public PageResult search(@RequestBody SearchRequest req) {
		int page = req.page() != null ? req.page() : 0;
		int size = req.size() != null ? req.size() : 50;
		Page<ConfigChangeInfo> result = configChangeService.search(req.from(), req.to(), req.targetType(), req.targetId(),
			req.actorId(), page, size);
		return new PageResult(result.getContent(), result.getTotalElements(), result.getNumber(), result.getSize());
	}

	@Operation(summary = "설정 변경 이력 구간", description = "화면의 구간 스크롤 표·엑셀(docs/range-grid-design.md §4). 최신순, 스냅샷 asOf(시각), 건수는 withTotal 일 때만.")
	@Requires(action = 'R')
	@PostMapping("/range")
	public RangeQueries.Result<ConfigChangeInfo> range(@RequestBody RangeQueries.Request<SearchRequest> req) {
		SearchRequest f = req.filter() != null ? req.filter() : new SearchRequest(null, null, null, null, null, null, null);
		return configChangeService.range(f.from(), f.to(), f.targetType(), f.targetId(), f.actorId(), req.rangeOrDefault());
	}

	@Operation(summary = "대상 하나의 변경 이력", description = "상세 화면의 변경 이력 탭용. 최신순.")
	@GetMapping("/{targetType}/{targetId}")
	public List<ConfigChangeInfo> historyOf(@PathVariable("targetType") TargetType targetType,
			@PathVariable("targetId") String targetId) {
		return configChangeService.historyOf(targetType, targetId);
	}
}
