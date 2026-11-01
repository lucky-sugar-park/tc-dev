package com.wow.be.tc.msg.audit.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.wow.be.framework.Condition.PageInfo;
import com.wow.be.framework.Condition.RangeInfo;
import com.wow.be.framework.Condition.SortDirection;
import com.wow.be.framework.Condition;
import com.wow.be.framework.persist.PageResponse;
import com.wow.be.framework.persist.PersistentService;
import com.wow.be.framework.persist.RangeResponse;
import com.wow.be.framework.persist.Rangeable;
import com.wow.be.framework.sse.EventPusher;
import com.wow.be.framework.sse.PushEventPayload;
import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.msg.audit.ExchangedMessageAuditEvent;
import com.wow.be.tc.msg.audit.ExchangedMessageAuditInfo;
import com.wow.be.tc.msg.audit.ExchangedMessageAuditService;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Service("auditMessageServiceBean")
@Slf4j
public class DefaultExchangedMessageAuditService implements ExchangedMessageAuditService, PersistentService<ExchangedMessageAuditInfo, ExchangedMessageAuditEntity>{

	@Autowired
	private ExchangedMessageAuditRepository exchangedMessageAuditRepository;
	
	@Autowired
	private ExchangedMessageAuditRangeableRepository exchangeMessageAuditRangeRepository;
	
//	@Autowired
//	private SimpleJpaRepository<ExchangedMessageAuditEntity, Long> simpleJpaRepository;

	@Override
	@Transactional
	public void writeAudit(ExchangedMessageAuditInfo auditInfo) {
		log.debug("");
		ExchangedMessageAuditEntity entity=new ExchangedMessageAuditEntity();
		entity.from(auditInfo);
		entity=exchangedMessageAuditRepository.save(entity);
		
		auditInfo.setId(entity.getId());
		PushEventPayload payload=new ExchangedMessageAuditEvent();
		payload.setEventId(UUID.randomUUID().toString());
		payload.setEventKind(ExchangedMessageAuditEvent.EVENT_KIND);
		payload.setData(auditInfo);
		payload.setComment("audit message");
		payload.setReconnectTime(3000L);
		EventPusher.newPusher().push(payload);
	}

	@Override
	public ExchangedMessageAuditInfo findMessageAuditById(long id) throws ToolControlException {
		log.debug("");
		ExchangedMessageAuditEntity entity=exchangedMessageAuditRepository.findById(id)
			.orElseThrow(() -> ToolControlException.of(ToolControlException.NOT_FOUND, "송수신 기록이 없습니다: " + id));
		return entity.info();
	}

	@Override
	public List<ExchangedMessageAuditInfo> findMessageAuditsByIds(List<Long> ids) {
		return exchangedMessageAuditRepository.findAllById(ids).stream().map(ExchangedMessageAuditEntity::info).toList();
	}

	@Override
	public List<ExchangedMessageAuditInfo> searchByMessageId(String messageId, String messageType) {
		return exchangedMessageAuditRepository.findByMessageIdAndMessageType(messageId, messageType).stream()
			.map(ExchangedMessageAuditEntity::info).toList();
	}

	@Override
	public List<ExchangedMessageAuditInfo> searchByTransactionId(String transactionId, String name, String messageType) {
		return exchangedMessageAuditRepository.findByTransactionIdAndNameAndMessageType(transactionId, name, messageType).stream()
			.map(ExchangedMessageAuditEntity::info).toList();
	}
	

	@Override
	public List<ExchangedMessageAuditInfo> searchMessageAudits() {
		log.debug("");
		return this.infos(exchangedMessageAuditRepository.findAll());
	}

	@Override
	public List<ExchangedMessageAuditInfo> searchMessageAudits(PageInfo pageInfo) {
		log.debug("");
		return infos(findToPage(pageInfo).getContent());
	}

	@Override
	public List<ExchangedMessageAuditInfo> searchMessageAudits(Condition cond) {
		log.debug("");
		ExchangedMessageAuditSpec specs=new ExchangedMessageAuditSpec();
		List<ExchangedMessageAuditEntity> exchangedMessageAuditEntityList=null;
		Specification<ExchangedMessageAuditEntity> specification=specs.createSpecification(cond);

		if(cond.getPageInfo()==null) {
			exchangedMessageAuditEntityList=exchangedMessageAuditRepository.findAll(specification);
		} else {
			PageInfo pInfo=cond.getPageInfo();
			Page<ExchangedMessageAuditEntity> page=findToPage(specification, pInfo);
			exchangedMessageAuditEntityList=page.getContent();
		}
		return infos(exchangedMessageAuditEntityList);
	}

	@Override
	public List<ExchangedMessageAuditInfo> searchMessageAudits(Condition cond, PageInfo pageInfo) {
		log.debug("");
		cond.setPageInfo(pageInfo);
		return searchMessageAudits(cond);
	}
	
	private Page<ExchangedMessageAuditEntity> findToPage(Specification<ExchangedMessageAuditEntity> specification, PageInfo pageInfo) {
		log.debug("");
		if(pageInfo==null) return null;
		
		Sort.Direction dir=pageInfo.getSortDirection()==SortDirection.ASCENDING?Sort.Direction.ASC:Sort.Direction.DESC;
		PageRequest prequest=PageRequest.of(pageInfo.getPage(), pageInfo.getSize(), dir, pageInfo.getSortBy());
		return exchangedMessageAuditRepository.findAll(specification, prequest);
	}
	
	private Page<ExchangedMessageAuditEntity> findToPage(PageInfo pageInfo) {
		log.debug("");
		Sort.Direction dir=pageInfo.getSortDirection()==SortDirection.ASCENDING?Sort.Direction.ASC:Sort.Direction.DESC;
		PageRequest prequest=PageRequest.of(pageInfo.getPage(), pageInfo.getSize(), dir, pageInfo.getSortBy());
		return exchangedMessageAuditRepository.findAll(prequest);
	}

	@Override
	public PageResponse<ExchangedMessageAuditInfo> searchMessageAuditsToPageResponse(PageInfo pageInfo) {
		log.debug("");
		PageResponse<ExchangedMessageAuditInfo> pageResp=new PageResponse<>();
		Page<ExchangedMessageAuditEntity> page=findToPage(pageInfo);
		pageResp.setPageInfo(pageInfo);
		pageResp.setTotalRowsCount(page.getTotalElements());
		pageResp.setTotalPages(page.getTotalPages());
		pageResp.setRows(infos(page.getContent()));
		
		return pageResp;
	}

	@Override
	public PageResponse<ExchangedMessageAuditInfo> searchMessageAuditsToPageResponse(Condition cond) {
		log.debug("");
		ExchangedMessageAuditSpec specs=new ExchangedMessageAuditSpec();
		List<ExchangedMessageAuditEntity> exchangedMessageAuditEntityList=null;
		Specification<ExchangedMessageAuditEntity> specification=specs.createSpecification(cond);

		PageResponse<ExchangedMessageAuditInfo> pageResp=new PageResponse<>();
		if(cond.getPageInfo()==null) {
			exchangedMessageAuditEntityList=exchangedMessageAuditRepository.findAll(specification);
			pageResp.setTotalPages(exchangedMessageAuditEntityList.size());
			pageResp.setPageInfo(null);
		} else {
			PageInfo pInfo=cond.getPageInfo();
			Page<ExchangedMessageAuditEntity> page=findToPage(specification, pInfo);
			exchangedMessageAuditEntityList=page.getContent();
			pageResp.setTotalRowsCount(page.getTotalElements());
			pageResp.setTotalPages(page.getTotalPages());
			pageResp.setPageInfo(pInfo);
		}
		pageResp.setRows(infos(exchangedMessageAuditEntityList));
		return pageResp;
	}

	@Override
	public PageResponse<ExchangedMessageAuditInfo> searchMessageAuditsAToPageResponse(Condition cond, PageInfo pageInfo) {
		log.debug("");
		cond.setPageInfo(pageInfo);
		return searchMessageAuditsToPageResponse(cond);
	}

	@Override
	public RangeResponse<ExchangedMessageAuditInfo> searchMessageAuditsByRange(Condition cond) {
		log.debug("");
		ExchangedMessageAuditSpec specs=new ExchangedMessageAuditSpec();
		List<ExchangedMessageAuditEntity> exchangedMessageAuditEntityList=null;
		Specification<ExchangedMessageAuditEntity> specification=specs.createSpecification(cond);
		
		RangeResponse<ExchangedMessageAuditInfo> rangeResp=new RangeResponse<>();
		if(cond.getRangeInfo()==null) {
			exchangedMessageAuditEntityList=exchangedMessageAuditRepository.findAll(specification);
			rangeResp.setTotalRowsCount(exchangedMessageAuditEntityList.size());
			rangeResp.setRangeInfo(null);
		} else {
			RangeInfo rInfo=cond.getRangeInfo();
			Sort.Direction dir=rInfo.getSortDirection()==SortDirection.ASCENDING?Sort.Direction.ASC:Sort.Direction.DESC;
			Rangeable rangeRequest=new Rangeable(rInfo.getStart(), rInfo.getLimit(), Sort.by(dir, rInfo.getSortBy()));
			exchangedMessageAuditEntityList=exchangeMessageAuditRangeRepository.findAllByRange(specification, rangeRequest);
			rangeResp.setTotalRowsCount(exchangedMessageAuditRepository.count(specification));
			rangeResp.setRangeInfo(rInfo);
			rangeResp.setRows(infos(exchangedMessageAuditEntityList));
		}
		return rangeResp;
	}

	@Override
	public RangeResponse<ExchangedMessageAuditInfo> searchMessageAuditsByRange(Condition cond, RangeInfo rangeInfo) {
		log.debug("");
		cond.setRangeInfo(rangeInfo);
		return searchMessageAuditsByRange(cond);
	}
}
