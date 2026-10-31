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
import com.wow.be.tc.security.RequestPrincipals;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 디스패치되는 요청·응답·실패를 감사 로그(EXCHANGED_MESSAGE_AUDIT)에 남긴다. 감사 서비스가 저장 뒤 SSE 로 화면에 알린다.
 *
 * <p>messageType 은 REQUEST / RESPONSE / ERROR 다. 레거시는 요청·응답만 남기고 실패는 남기지 않았다.
 * 감사 저장이 실패해도 PLC 처리는 막지 않는다 — 경고 로그만 남긴다.
 *
 * <p>callback 을 바라는 요청(pushResult·pushUrl)은 REQUEST 의 DESCRIPTION 에 {@code pushUrl: 주소} 를 남긴다 — 응답 기록으로
 * callback 주소에 다시 보낼 때 쓴다(docs/redelivery-design.md §2 빈 곳 3).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditDispatchInterceptor implements DispatchInterceptor {

	/** REQUEST 감사 DESCRIPTION 의 callback 주소 머리말 */
	public static final String PUSH_URL_PREFIX = "pushUrl: ";

	/** 감사 DESCRIPTION 칼럼 길이. 넘는 주소는 잘려 쓸 수 없으므로 남기지 않는다 */
	private static final int DESCRIPTION_LENGTH = 1024;

	private final ExchangedMessageAuditService auditService;

	@Override
	public void preDispatch(RequestMessage request) {
		write(request, audit("REQUEST", request.getTransactionId(), request.getMessageId(), request.getOpcode(),
			request.getTimestamp(), request.getSender(), request.getReceiver(), request.getData(), requestDescription(request)));
	}

	private static String requestDescription(RequestMessage request) {
		if (!(request instanceof TcMessageRequest tc) || !tc.wantsCallback()) {
			return "";
		}
		String description = PUSH_URL_PREFIX + tc.getPushUrl();
		if (description.length() > DESCRIPTION_LENGTH) {
			log.warn("callback 주소가 길어 감사에 남기지 않습니다(응답 기록으로 다시 보낼 수 없음): opcode={}, transactionId={}",
				tc.getOpcode(), tc.getTransactionId());
			return "";
		}
		return description;
	}

	@Override
	public void postDispatch(RequestMessage request, ResponseMessage response) {
		String description = Boolean.FALSE.equals(response.getSuccess())
			? response.getErrorCode() + ": " + response.getErrorMessage() : "";
		write(request, audit("RESPONSE", response.getTransactionId(), response.getMessageId(), response.getOpcode(),
			response.getTimestamp(), response.getSender(), response.getReceiver(), response.getData(), description));
	}

	@Override
	public void onError(RequestMessage request, Throwable cause) {
		String errorCode = cause instanceof MesGeneralException mes ? mes.getErrorCode() : null;
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("errorCode", errorCode);
		data.put("errorMessage", cause.getMessage());
		write(request, audit("ERROR", request.getTransactionId(), RequestMessage.generateMessageId(), request.getOpcode(),
			System.currentTimeMillis(), request.getReceiver(), request.getSender(), data,
			errorCode + ": " + cause.getMessage()));
	}

	private void write(RequestMessage request, ExchangedMessageAuditInfo info) {
		// 실제 호출자 — 컨트롤러가 요청 스레드에서 찍어 둔 값. 없으면(스케줄 Job 등 내부) system
		String principal = request instanceof TcMessageRequest tc ? tc.getPrincipal() : null;
		info.setPrincipal(principal != null ? principal : RequestPrincipals.SYSTEM);
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
