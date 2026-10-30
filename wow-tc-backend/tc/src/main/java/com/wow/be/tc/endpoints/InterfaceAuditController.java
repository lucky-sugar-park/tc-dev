package com.wow.be.tc.endpoints;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
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
import com.wow.be.tc.msg.audit.ExchangedMessageAuditInfo;
import com.wow.be.tc.msg.audit.ExchangedMessageAuditService;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping(value="/api/v1/ifsvc/audit")
@Slf4j
// 권한(설계 §4) — 기본 동작과 다른 메서드(제어·사용 변경 POST, 조회용 POST)는 동작을 명시한다(클래스 선언은 GET 을 검사하지 않고 POST 를 C 로 본다)
@Requires("tc.audit")
public class InterfaceAuditController {

	@Autowired
	private ExchangedMessageAuditService exchangedMessageAuditService;
	
	@Requires(action = 'D')
	@RequestMapping(value="/delete/{id}", method=RequestMethod.DELETE)
	@ResponseStatus(value = HttpStatus.OK)
	public void deleteMessageAudit(@PathVariable("id") long id) {
		log.debug("");
		exchangedMessageAuditService.deleteMessageAudit(id);
	}
	
	@Requires(action = 'D')
	@RequestMapping(value="/delete", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	public void deleteMessageAuditList(@RequestBody List<Long> idArr) {
		log.debug("");
		exchangedMessageAuditService.deleteMessageAudits(idArr);
	}
	
	@Requires(action = 'D')
	@RequestMapping(value="/delete/timestamp", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public Map<String, Long> deleteMessageByTimestamp(@RequestBody Date timestamp) {
		log.debug("");
		long count=exchangedMessageAuditService.deleteMessageAudits(timestamp);
		Map<String, Long> map=new HashMap<>();
		map.put("deletedCount", count);
		return map;
	}
	
	@RequestMapping(value="/find/{id}", method=RequestMethod.GET)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public ExchangedMessageAuditInfo findMessageAudit(@PathVariable("id") long id) {
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
	
	@Requires(action = 'R')
	@RequestMapping(value="/search/range", method=RequestMethod.POST)
	@ResponseStatus(value = HttpStatus.OK)
	@ResponseBody
	public RangeResponse<ExchangedMessageAuditInfo> searchMessageAuditByRange(@RequestBody Condition cond) {
		log.debug("");
		return exchangedMessageAuditService.searchMessageAuditsByRange(cond);
	}
}
