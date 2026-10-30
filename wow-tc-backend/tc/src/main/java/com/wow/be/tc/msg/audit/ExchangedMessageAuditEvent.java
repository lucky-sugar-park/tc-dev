package com.wow.be.tc.msg.audit;

import com.wow.be.framework.sse.PushEventPayload;

/**
 * 감사 메시지 저장을 화면에 알리는 SSE 이벤트.
 */
public class ExchangedMessageAuditEvent extends PushEventPayload {

	/** SSE 이벤트 이름 */
	public static final String EVENT_KIND = "audit_message";
	public ExchangedMessageAuditEvent() {
	}

	@Override
	public String toJson() {
		ExchangedMessageAuditInfo info=(ExchangedMessageAuditInfo) super.getData();
		return info.toJson(false);
	}
}
