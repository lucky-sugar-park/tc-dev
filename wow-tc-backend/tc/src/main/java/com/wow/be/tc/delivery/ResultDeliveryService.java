package com.wow.be.tc.delivery;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.wow.be.tc.dispatch.TcMessageRequest;
import com.wow.be.tc.dispatch.TcMessageResponse;

/**
 * 디스패치 결과(성공·실패 모두)를 정의된 대상에 전달한다.
 *
 * <p>대상은 두 곳에서 정해진다.
 * <ul>
 *   <li>요청: {@code pushResult=true} 와 {@code pushUrl} — callback 주소 (비동기 요청만)</li>
 *   <li>인터페이스: 응답 인터페이스의 WebHook 목록</li>
 * </ul>
 * 대상마다 서로 독립적으로 전용 스레드 풀에서 보낸다. 한 대상의 실패가 다른 대상을 막지 않는다.
 */
public interface ResultDeliveryService {

	/**
	 * 전달 대상을 정한다.
	 *
	 * @param includeCallback 요청의 callback 주소를 포함할지 — 동기 응답({@code /request-and-reply})은 결과를 응답 본문으로
	 *                        이미 돌려주므로 false
	 */
	List<DeliveryTarget> targetsOf(TcMessageRequest request, boolean includeCallback);

	/**
	 * 대상들로 비동기 전달한다.
	 *
	 * @return 모든 대상의 전달이 끝나면(성공·실패 무관) 완료되는 future. 기다릴 필요가 없으면 무시한다.
	 */
	CompletableFuture<Void> deliver(List<DeliveryTarget> targets, TcMessageRequest request, TcMessageResponse response);
}
