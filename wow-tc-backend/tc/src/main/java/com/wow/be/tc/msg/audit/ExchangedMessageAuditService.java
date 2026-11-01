package com.wow.be.tc.msg.audit;

import java.util.List;

import com.wow.be.framework.Condition.PageInfo;
import com.wow.be.framework.Condition.RangeInfo;
import com.wow.be.framework.Condition;
import com.wow.be.framework.persist.PageResponse;
import com.wow.be.framework.persist.RangeResponse;
import com.wow.be.tc.ToolControlException;

public interface ExchangedMessageAuditService {

	public abstract void writeAudit(ExchangedMessageAuditInfo auditInfo);

	public abstract ExchangedMessageAuditInfo findMessageAuditById(long id) throws ToolControlException;

	/** 있는 것만 돌려준다(없는 ID 는 빠진다). */
	public abstract List<ExchangedMessageAuditInfo> findMessageAuditsByIds(List<Long> ids);

	public abstract List<ExchangedMessageAuditInfo> searchByMessageId(String messageId, String messageType);

	public abstract List<ExchangedMessageAuditInfo> searchByTransactionId(String transactionId, String name, String messageType);
	
	public abstract List<ExchangedMessageAuditInfo> searchMessageAudits();
	
	public abstract List<ExchangedMessageAuditInfo> searchMessageAudits(PageInfo pageInfo);
	
	public abstract PageResponse<ExchangedMessageAuditInfo> searchMessageAuditsToPageResponse(PageInfo pageInfo);

	public abstract List<ExchangedMessageAuditInfo> searchMessageAudits(Condition cond);
	
	public abstract PageResponse<ExchangedMessageAuditInfo> searchMessageAuditsToPageResponse(Condition cond);

	public abstract List<ExchangedMessageAuditInfo> searchMessageAudits(Condition cond, PageInfo pageInfo);
	
	public abstract PageResponse<ExchangedMessageAuditInfo> searchMessageAuditsAToPageResponse(Condition cond, PageInfo pageInfo);
	
	public abstract RangeResponse<ExchangedMessageAuditInfo> searchMessageAuditsByRange(Condition cond);
	
	public abstract RangeResponse<ExchangedMessageAuditInfo> searchMessageAuditsByRange(Condition cond, RangeInfo rangeInfo);
}
