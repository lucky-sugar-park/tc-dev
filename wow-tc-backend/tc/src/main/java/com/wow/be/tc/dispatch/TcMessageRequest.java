package com.wow.be.tc.dispatch;

import com.wow.be.framework.dispatch.RequestMessage;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * tc 요청 메시지. framework {@link RequestMessage}(transactionId·messageId·opcode·timestamp·sender·receiver·data)에
 * 결과를 따로 돌려받을 주소(callback)를 더한다.
 *
 * <p>JSON 형식은 레거시와 같다. 레거시의 {@code sync}·{@code shouldReply} 필드는 없앴다 — 동기/비동기는 호출한
 * 엔드포인트가 정한다({@code /request-and-reply} 동기, {@code /request} 비동기). 이 필드를 보내도 무시된다.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TcMessageRequest extends RequestMessage {

	/** 처리 결과를 {@link #pushUrl} 로 보내 달라는 표시. 비동기 요청({@code /request})에서만 쓴다. */
	private boolean pushResult;

	/** 처리 결과를 받을 주소(PUT). {@link #pushResult} 가 true 일 때만 쓴다. */
	private String pushUrl;

	public TcMessageRequest() {
		super();
		// 요청 시각 기본값. 디스패처도 비어 있으면 채우지만, 감사 기록 전에 값이 있게 만들 때 채워 둔다.
		setTimestamp(System.currentTimeMillis());
	}

	/** 결과를 callback 주소로 보내야 하는 요청인가 */
	public boolean wantsCallback() {
		return pushResult && pushUrl != null && !pushUrl.isBlank();
	}
}
