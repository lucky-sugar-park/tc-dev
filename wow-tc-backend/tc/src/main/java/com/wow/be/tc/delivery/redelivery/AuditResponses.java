package com.wow.be.tc.delivery.redelivery;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wow.be.tc.delivery.DeliveryTarget.DeliveryType;
import com.wow.be.tc.dispatch.AuditDispatchInterceptor;
import com.wow.be.tc.dispatch.TcMessageResponse;
import com.wow.be.tc.msg.audit.ExchangedMessageAuditInfo;

/**
 * 송수신 감사 기록에서 보냈던 응답과 대상을 다시 만든다 (docs/redelivery-design.md §2, §4).
 */
public final class AuditResponses {

	public static final String REQUEST = "REQUEST";

	public static final String RESPONSE = "RESPONSE";

	public static final String ERROR = "ERROR";

	public static final String DELIVERY = "DELIVERY";

	private static final ObjectMapper JSON = new ObjectMapper()
		.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

	private AuditResponses() {
	}

	/** DELIVERY 감사의 {@code MSG_JSON.response} — 보낸 응답 그대로. 보완 전 기록({@code {}})이면 null */
	public static TcMessageResponse fromDeliveryBody(Map<String, Object> dataMap, String responseKey) {
		Object body = dataMap != null ? dataMap.get(responseKey) : null;
		if (!(body instanceof Map<?, ?> map)) {
			return null;
		}
		return JSON.convertValue(map, TcMessageResponse.class);
	}

	/**
	 * RESPONSE 감사 → 응답. data 는 MSG_JSON, 실패 정보는 DESCRIPTION({@code 코드: 메시지}). PLC 종료 코드와 changed 는 기록에 없어 비운다.
	 */
	public static TcMessageResponse fromResponseAudit(ExchangedMessageAuditInfo info) {
		TcMessageResponse response = header(info);
		response.setData(info.getDataMap() != null ? new LinkedHashMap<>(info.getDataMap()) : new LinkedHashMap<>());
		String description = info.getDescription();
		if (description == null || description.isBlank()) {
			response.setSuccess(true);
			return response;
		}
		response.setSuccess(false);
		int at = description.indexOf(": ");
		response.setErrorCode(nullable(at > 0 ? description.substring(0, at) : description));
		response.setErrorMessage(at > 0 ? nullable(description.substring(at + 2)) : null);
		return response;
	}

	/** ERROR 감사(디스패치 예외) → 실패 응답 */
	public static TcMessageResponse fromErrorAudit(ExchangedMessageAuditInfo info) {
		TcMessageResponse response = header(info);
		Map<String, Object> data = info.getDataMap() != null ? info.getDataMap() : Map.of();
		response.setSuccess(false);
		response.setErrorCode(text(data.get("errorCode")));
		response.setErrorMessage(text(data.get("errorMessage")));
		return response;
	}

	/** 감사의 헤더 칼럼. 감사의 SENDER·RECEIVER 는 응답 기준으로 이미 바뀌어 있다 */
	private static TcMessageResponse header(ExchangedMessageAuditInfo info) {
		TcMessageResponse response = new TcMessageResponse();
		response.setTransactionId(info.getTransactionId());
		response.setMessageId(info.getMessageId());
		response.setOpcode(info.getName());
		response.setTimestamp(info.getTimestamp() != null ? info.getTimestamp().getTime() : null);
		response.setSender(info.getSender());
		response.setReceiver(info.getReceiver());
		return response;
	}

	/**
	 * DELIVERY 감사의 RECEIVER({@code 유형:이름:주소·테이블}). 주소에 {@code :} 가 있어 세 조각까지만 나눈다.
	 *
	 * @return 읽을 수 없으면 null
	 */
	public static Receiver parseReceiver(String receiver) {
		if (receiver == null) {
			return null;
		}
		String[] parts = receiver.split(":", 3);
		if (parts.length < 3 || parts[1].isBlank() || parts[2].isBlank() || "null".equals(parts[2])) {
			return null;
		}
		try {
			return new Receiver(DeliveryType.valueOf(parts[0]), parts[1], parts[2]);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/**
	 * REQUEST 감사에 남은 callback 주소({@code pushUrl: 주소}).
	 *
	 * @return 없으면(callback 을 바라지 않은 요청, 이 기능 전 기록) null
	 */
	public static String pushUrlOf(ExchangedMessageAuditInfo request) {
		String description = request != null ? request.getDescription() : null;
		if (description == null || !description.startsWith(AuditDispatchInterceptor.PUSH_URL_PREFIX)) {
			return null;
		}
		String url = description.substring(AuditDispatchInterceptor.PUSH_URL_PREFIX.length()).trim();
		return url.isEmpty() ? null : url;
	}

	/** @param destination HTTP 면 주소, DB_TABLE 이면 테이블 */
	public record Receiver(DeliveryType type, String name, String destination) {
	}

	private static String text(Object value) {
		return value != null ? nullable(String.valueOf(value)) : null;
	}

	/** 감사에 문자열 "null" 로 남은 값 */
	private static String nullable(String value) {
		return value == null || "null".equals(value) ? null : value;
	}
}
