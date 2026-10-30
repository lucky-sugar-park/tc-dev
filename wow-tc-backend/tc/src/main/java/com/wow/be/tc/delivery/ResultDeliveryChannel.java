package com.wow.be.tc.delivery;

import com.wow.be.tc.dispatch.TcMessageRequest;
import com.wow.be.tc.dispatch.TcMessageResponse;

/**
 * 처리 결과를 한 가지 방식으로 전달한다. 전달 유형마다 빈 하나를 둔다 — 새 방식(DB 테이블, Kafka 등)은 빈을 더하면 된다.
 */
public interface ResultDeliveryChannel {

	/** 이 채널이 맡는 전달 유형 */
	DeliveryTarget.DeliveryType type();

	/**
	 * 결과를 보낸다. 호출 스레드는 전달 전용 스레드 풀이다.
	 *
	 * @throws RuntimeException 보내지 못했을 때 — {@link ResultDeliveryService} 가 기록한다
	 */
	void deliver(DeliveryTarget target, TcMessageRequest request, TcMessageResponse response);
}
