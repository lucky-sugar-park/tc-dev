package com.wow.be.tc.endpoints;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.wow.be.framework.security.authz.Requires;
import com.wow.be.framework.Condition;
import com.wow.be.framework.persist.PageResponse;
import com.wow.be.framework.persist.RangeResponse;
import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.msg.audit.AuditQuery;
import com.wow.be.tc.msg.audit.ExchangedMessageAuditInfo;
import com.wow.be.tc.msg.audit.impl.AuditQueryService;
import com.wow.be.tc.msg.audit.ExchangedMessageAuditService;
import com.wow.be.tc.utils.RangeQueries;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(value="/api/v1/ifsvc/audit")
@Slf4j
// 권한(설계 §4) — 기본 동작과 다른 메서드(제어·사용 변경 POST, 조회용 POST)는 동작을 명시한다(클래스 선언은 GET 을 검사하지 않고 POST 를 C 로 본다)
// 감사는 조회만 — 지우는 것은 보관 정리 Job 뿐이다(docs/data-retention.md R2·§6.3)
@Requires("tc.audit")
public class InterfaceAuditController {

	@Autowired
	private ExchangedMessageAuditService exchangedMessageAuditService;

	@Autowired
	private AuditQueryService auditQueryService;
	
	@RequestMapping(value="/find/{id}", method=RequestMethod.GET)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public ExchangedMessageAuditInfo findMessageAudit(@PathVariable("id") long id) throws ToolControlException {
		log.debug("");
		return exchangedMessageAuditService.findMessageAuditById(id);
	}
	
	@RequestMapping(value="/search", method=RequestMethod.GET)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public List<ExchangedMessageAuditInfo> searchMessageAudit() {
		log.debug("");
		return exchangedMessageAuditService.searchMessageAudits();
	}
	
	@Requires(action = 'R')
	@RequestMapping(value="/search/page", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public PageResponse<ExchangedMessageAuditInfo> searchMessageAudit(@RequestBody Condition cond) {
		log.debug("");
		return exchangedMessageAuditService.searchMessageAuditsToPageResponse(cond);
	}
	
	/**
	 * 화면용 검색 — 기간·opcode·거래 ID·종류·실패만·호출자, 최신순 쪽(docs/frontend-design.md §14). framework 일반 검색은 날짜 범위를 받지 못한다.
	 */
	@Requires(action = 'R')
	@RequestMapping(value="/search/query", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public AuditQuery.Page query(@RequestBody AuditQuery query) {
		return auditQueryService.search(query);
	}

	/** 구간 조회 본문 — 화면 조건, 구간, 본문(MSG_JSON) 포함 여부(기본 뺌) */
	public record RangeRequest(AuditQuery filter, RangeQueries.Params range, Boolean includeBody) {
	}

	/**
	 * 화면용 구간 조회 — 화면의 구간 스크롤 표·엑셀(docs/range-grid-design.md §4). 최신순, 스냅샷 asOf(ID), 건수는 withTotal 일 때만.
	 */
	@Requires(action = 'R')
	@PostMapping("/search/query/range")
	public RangeQueries.Result<ExchangedMessageAuditInfo> queryRange(@RequestBody RangeRequest req) {
		AuditQuery filter = req.filter() != null ? req.filter() : new AuditQuery(null, null, null, null, null, null, null, null, null);
		RangeQueries.Params range = req.range() != null ? req.range() : new RangeQueries.Params(0, 100, null, true);
		return auditQueryService.range(filter, range, Boolean.TRUE.equals(req.includeBody()));
	}

	@Requires(action = 'R')
	@RequestMapping(value="/search/range", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public RangeResponse<ExchangedMessageAuditInfo> searchMessageAuditByRange(@RequestBody Condition cond) {
		log.debug("");
		return exchangedMessageAuditService.searchMessageAuditsByRange(cond);
	}
}
