package com.wow.be.framework.dispatch;

import org.springframework.core.Ordered;

import com.wow.be.framework.MesGeneralException;

/**
 * 디스패치 앞·뒤·오류 시점에 끼어드는 지점. 감사 기록, SSE 알림, 로그 추적 같은 공통 처리를 커맨드 밖에 둔다.
 *
 * <p>빈으로 등록하면 {@link MessageDispatcher} 가 {@link #getOrder()} 순서(작을수록 먼저)로 모두 호출한다.
 * <ul>
 *   <li>{@link #preDispatch} 에서 예외를 던지면 디스패치가 중단되고 {@link #onError} 가 호출된다.</li>
 *   <li>{@link #postDispatch}, {@link #onError} 안의 예외는 디스패치 결과를 바꾸지 않는다 — 경고 로그만 남는다.</li>
 *   <li>한 번의 디스패치에서 {@link #postDispatch} 와 {@link #onError} 중 하나만 호출된다.</li>
 * </ul>
 *
 * <p>비동기 디스패치에서는 {@code dispatchTaskExecutor} 의 작업 스레드에서 호출된다.
 */
public interface DispatchInterceptor extends Ordered {

	/**
	 * 커맨드를 찾기 전에 호출된다. 요청 헤더(transactionId·messageId·timestamp)는 이미 채워져 있다.
	 *
	 * @param request 요청
	 * @throws MesGeneralException 디스패치를 막아야 할 때
	 */
	default void preDispatch(RequestMessage request) throws MesGeneralException {
	}

	/**
	 * 커맨드가 응답을 돌려준 뒤 호출된다. 응답이 업무 실패({@code success=false})여도 여기로 온다.
	 *
	 * @param request  요청
	 * @param response 커맨드가 돌려준 응답
	 */
	default void postDispatch(RequestMessage request, ResponseMessage response) {
	}

	/**
	 * 디스패치가 응답 없이 끝났을 때 호출된다(커맨드 없음, 실행 예외, 스레드 풀 포화, preDispatch 거부).
	 *
	 * @param request 요청
	 * @param cause   원인. 프레임워크 밖 예외는 {@link DispatchException}({@code ERR_500_DISPATCH}) 으로 감싸져 있다.
	 */
	default void onError(RequestMessage request, MesGeneralException cause) {
	}

	@Override
	default int getOrder() {
		return 0;
	}
}
