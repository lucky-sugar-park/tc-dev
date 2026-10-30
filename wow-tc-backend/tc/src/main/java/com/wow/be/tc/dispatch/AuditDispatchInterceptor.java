package com.wow.be.tc.dispatch;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.wow.be.framework.MesGeneralException;
import com.wow.be.framework.dispatch.DispatchInterceptor;
import com.wow.be.framework.dispatch.RequestMessage;
import com.wow.be.framework.dispatch.ResponseMessage;
import com.wow.be.tc.msg.audit.ExchangedMessageAuditInfo;
import com.wow.be.tc.msg.audit.ExchangedMessageAuditService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 디스패치되는 요청·응답·실패를 감사 로그(EXCHANGED_MESSAGE_AUDIT)에 남긴다. 감사 서비스가 저장 뒤 SSE 로 화면에 알린다.
 *
 * <p>messageType 은 REQUEST / RESPONSE / ERROR 다. 레거시는 요청·응답만 남기고 실패는 남기지 않았다.
 * 감사 저장이 실패해도 PLC 처리는 막지 않는다 — 경고 로그만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditDispatchInterceptor implements DispatchInterceptor {

	private final ExchangedMessageAuditService auditService;

	@Override
	public void preDispatch(RequestMessage request) {
		write(audit("REQUEST", request.getTransactionId(), request.getMessageId(), request.getOpcode(),
			request.getTimestamp(), request.getSender(), request.getReceiver(), request.getData(), ""));
	}

	@Override
	public void postDispatch(RequestMessage request, ResponseMessage response) {
		String description = Boolean.FALSE.equals(response.getSuccess())
			? response.getErrorCode() + ": " + response.getErrorMessage() : "";
		write(audit("RESPONSE", response.getTransactionId(), response.getMessageId(), response.getOpcode(),
			response.getTimestamp(), response.getSender(), response.getReceiver(), response.getData(), description));
	}

	@Override
	public void onError(RequestMessage request, Throwable cause) {
		String errorCode = cause instanceof MesGeneralException mes ? mes.getErrorCode() : null;
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("errorCode", errorCode);
		data.put("errorMessage", cause.getMessage());
		write(audit("ERROR", request.getTransactionId(), RequestMessage.generateMessageId(), request.getOpcode(),
			System.currentTimeMillis(), request.getReceiver(), request.getSender(), data,
			errorCode + ": " + cause.getMessage()));
	}

	private void write(ExchangedMessageAuditInfo info) {
		try {
			auditService.writeAudit(info);
		} catch (RuntimeException e) {
			log.warn("감사 로그를 남기지 못했습니다: type={}, opcode={}, transactionId={}",
				info.getMessageType(), info.getName(), info.getTransactionId(), e);
		}
	}

	private static ExchangedMessageAuditInfo audit(String type, String transactionId, String messageId, String opcode,
			Long timestamp, String sender, String receiver, Map<String, Object> data, String description) {
		ExchangedMessageAuditInfo info = new ExchangedMessageAuditInfo();
		info.setTransactionId(transactionId);
		info.setMessageId(messageId);
		info.setName(opcode);
		info.setMessageType(type);
		info.setTimestamp(new Date(timestamp != null ? timestamp : System.currentTimeMillis()));
		info.setSender(sender);
		info.setReceiver(receiver);
		info.setDataMap(data);
		info.setDescription(description);
		return info;
	}
}
