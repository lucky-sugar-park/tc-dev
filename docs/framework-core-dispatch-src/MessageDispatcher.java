package com.wow.be.framework.dispatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.wow.be.framework.MesGeneralException;

import lombok.extern.slf4j.Slf4j;

/**
 * opcode 로 커맨드를 찾아 실행한다.
 *
 * <p>처리 순서
 * <ol>
 *   <li>요청 검증 — 요청이 없거나 opcode 가 비어 있으면 {@code ERR_400}</li>
 *   <li>헤더 보충 — 비어 있는 transactionId·messageId 생성, timestamp 가 0 이면 현재 시각</li>
 *   <li>{@link DispatchInterceptor#preDispatch}</li>
 *   <li>{@link CommandResolver} 를 순서대로 돌려 처음 찾은 커맨드 선택 — 아무도 못 찾으면 {@code ERR_404_DISPATCH_COMMAND}</li>
 *   <li>{@link MessageCommand#execute}</li>
 *   <li>{@link DispatchInterceptor#postDispatch} — 3~5 에서 실패하면 대신 {@link DispatchInterceptor#onError}</li>
 * </ol>
 * 1·2 단계의 실패는 인터셉터를 거치지 않는다(요청 자체가 성립하지 않으므로).
 *
 * <p>실패는 항상 {@link MesGeneralException} 으로 전한다. 프레임워크 밖 예외(NPE 등)는 {@code ERR_500_DISPATCH} 로
 * 감싸고 원래 예외는 {@code getCause()} 로 남긴다.
 *
 * <p>커맨드는 요청 타입을 제네릭으로 선언하지만, 디스패처는 opcode 만 보고 커맨드를 고른다. 커맨드가 기대하는
 * 하위 요청 타입(예: TcMessageRequest)과 실제 요청 타입이 다르면 실행 중 {@code ClassCastException} 이 나고
 * {@code ERR_500_DISPATCH} 로 보고된다 — 요청 타입을 맞추는 것은 앱의 책임이다.
 */
@Slf4j
@Component
public class MessageDispatcher {

	private static final String MDC_TRANSACTION_ID = "transactionId";

	private static final String MDC_OPCODE = "opcode";

	private final List<CommandResolver> resolvers;

	private final List<DispatchInterceptor> interceptors;

	private final Executor executor;

	public MessageDispatcher(
			List<CommandResolver> resolvers,
			ObjectProvider<DispatchInterceptor> interceptors,
			@Qualifier(DispatchConfig.DISPATCH_TASK_EXECUTOR) Executor executor) {
		List<CommandResolver> sorted = new ArrayList<>(resolvers);
		AnnotationAwareOrderComparator.sort(sorted);
		this.resolvers = List.copyOf(sorted);
		this.interceptors = interceptors.orderedStream().toList();
		this.executor = executor;

		log.info("MessageDispatcher 준비 완료 — 리졸버: {}, 인터셉터: {}",
			this.resolvers.stream().map(r -> r.getClass().getSimpleName()).toList(),
			this.interceptors.stream().map(i -> i.getClass().getSimpleName()).toList());
	}

	// ── 동기 ─────────────────────────────────────────────────────────

	/**
	 * 호출 스레드에서 실행하고 응답을 돌려준다.
	 *
	 * @param request 요청. 비어 있는 헤더는 이 메서드가 채운다.
	 * @return 커맨드가 돌려준 응답 (업무 실패면 {@code success=false})
	 * @throws MesGeneralException 응답을 만들지 못했을 때
	 */
	public <R extends ResponseMessage> R dispatch(RequestMessage request) throws MesGeneralException {
		prepare(request);
		return execute(request);
	}

	// ── 비동기 ───────────────────────────────────────────────────────

	/**
	 * {@code dispatchTaskExecutor} 에서 실행한다. 이 메서드는 예외를 던지지 않는다 — 모든 실패는 future 로 전한다.
	 * future 의 예외는 {@link CompletionException} 으로 감싸지지 않은 {@link MesGeneralException} 이다
	 * ({@code future.get()} 에서는 {@link ExecutionException} 의 cause 로 나온다).
	 *
	 * @param request 요청. 비어 있는 헤더는 호출 스레드에서 미리 채운다 — 호출자가 transactionId 를 바로 쓸 수 있다.
	 */
	public <R extends ResponseMessage> CompletableFuture<R> dispatchAsync(RequestMessage request) {
		try {
			prepare(request);
		} catch (MesGeneralException e) {
			return CompletableFuture.failedFuture(e);
		}

		CompletableFuture<R> future = new CompletableFuture<>();
		try {
			executor.execute(() -> {
				try {
					future.complete(execute(request));
				} catch (MesGeneralException e) {
					future.completeExceptionally(e);
				} catch (RuntimeException e) {          // execute 가 이미 감싸지만, 혹시 모를 누락을 막는다
					future.completeExceptionally(toMesException(e));
				}
			});
		} catch (RejectedExecutionException e) {      // Spring TaskRejectedException 도 여기로 온다
			MesGeneralException rejected = toMesException(e);
			notifyError(request, rejected);
			future.completeExceptionally(rejected);
		}
		return future;
	}

	/**
	 * 비동기로 실행하고 결과를 콜백으로 알린다. {@link MessageCallback#onCompleted} 와
	 * {@link MessageCallback#onError} 중 정확히 하나가 한 번 호출된다. 콜백 안의 예외는 경고 로그만 남긴다.
	 */
	public <R extends ResponseMessage> void dispatch(RequestMessage request, MessageCallback<R> callback) {
		Objects.requireNonNull(callback, "callback");
		this.<R>dispatchAsync(request).whenComplete((response, error) -> {
			try {
				if (error == null) callback.onCompleted(response);
				else callback.onError(request, toMesException(error));
			} catch (RuntimeException callbackError) {
				log.warn("디스패치 콜백 처리 중 오류가 났습니다: opcode={}, transactionId={}",
					opcodeOf(request), transactionIdOf(request), callbackError);
			}
		});
	}

	// ── 내부 ─────────────────────────────────────────────────────────

	/** 1·2 단계: 검증과 헤더 보충. 인터셉터를 거치지 않는다. */
	private void prepare(RequestMessage request) throws MesGeneralException {
		if (request == null) {
			throw new DispatchException(DispatchException.INVALID_REQUEST, "요청이 비어 있습니다.");
		}
		if (!StringUtils.hasText(request.getOpcode())) {
			throw new DispatchException(DispatchException.INVALID_REQUEST, "요청에 opcode 가 없습니다.");
		}
		if (!StringUtils.hasText(request.getTransactionId())) {
			request.setTransactionId(RequestMessage.generateTransactionId());
		}
		if (!StringUtils.hasText(request.getMessageId())) {
			request.setMessageId(RequestMessage.generateMessageId());
		}
		if (request.getTimestamp() <= 0) {
			request.setTimestamp(System.currentTimeMillis());
		}
	}

	/** 3~6 단계: 인터셉터·커맨드 실행. prepare 를 거친 요청만 들어온다. */
	private <R extends ResponseMessage> R execute(RequestMessage request) throws MesGeneralException {
		MDC.put(MDC_TRANSACTION_ID, request.getTransactionId());
		MDC.put(MDC_OPCODE, request.getOpcode());
		try {
			for (DispatchInterceptor interceptor : interceptors) {
				interceptor.preDispatch(request);
			}

			MessageCommand<RequestMessage, R> command = resolve(request);
			log.debug("커맨드 실행: opcode={}, command={}", request.getOpcode(), command.getClass().getName());

			R response = command.execute(request);
			if (response == null) {
				throw new DispatchException(DispatchException.EXECUTION_FAILED,
					"커맨드가 응답을 돌려주지 않았습니다: " + request.getOpcode());
			}

			notifyPost(request, response);
			return response;
		} catch (MesGeneralException | RuntimeException e) {
			MesGeneralException cause = toMesException(e);
			notifyError(request, cause);
			throw cause;
		} finally {
			MDC.remove(MDC_TRANSACTION_ID);
			MDC.remove(MDC_OPCODE);
		}
	}

	@SuppressWarnings("unchecked")
	private <R extends ResponseMessage> MessageCommand<RequestMessage, R> resolve(RequestMessage request)
			throws DispatchException {
		for (CommandResolver resolver : resolvers) {
			Optional<MessageCommand<?, ?>> command = resolver.resolve(request);
			if (command.isPresent()) {
				return (MessageCommand<RequestMessage, R>) command.get();
			}
		}
		throw new DispatchException(DispatchException.COMMAND_NOT_FOUND,
			"요청을 처리할 커맨드가 없습니다: " + request.getOpcode());
	}

	private void notifyPost(RequestMessage request, ResponseMessage response) {
		for (DispatchInterceptor interceptor : interceptors) {
			try {
				interceptor.postDispatch(request, response);
			} catch (RuntimeException e) {
				log.warn("디스패치 인터셉터(postDispatch) 처리 중 오류가 났습니다: interceptor={}, opcode={}",
					interceptor.getClass().getName(), request.getOpcode(), e);
			}
		}
	}

	private void notifyError(RequestMessage request, MesGeneralException cause) {
		for (DispatchInterceptor interceptor : interceptors) {
			try {
				interceptor.onError(request, cause);
			} catch (RuntimeException e) {
				log.warn("디스패치 인터셉터(onError) 처리 중 오류가 났습니다: interceptor={}, opcode={}",
					interceptor.getClass().getName(), request.getOpcode(), e);
			}
		}
	}

	/** 어떤 예외든 MesGeneralException 으로 정리한다. CompletionException·ExecutionException 은 벗긴다. */
	static MesGeneralException toMesException(Throwable error) {
		Throwable t = error;
		while ((t instanceof CompletionException || t instanceof ExecutionException) && t.getCause() != null) {
			t = t.getCause();
		}
		if (t instanceof MesGeneralException mes) {
			return mes;
		}
		if (t instanceof RejectedExecutionException) {
			return new DispatchException(DispatchException.REJECTED,
				"요청이 많아 지금은 처리할 수 없습니다. 잠시 뒤 다시 시도해 주세요.", t);
		}
		return new DispatchException(DispatchException.EXECUTION_FAILED, "요청을 처리하지 못했습니다.", t);
	}

	private static String opcodeOf(RequestMessage request) {
		return request != null ? request.getOpcode() : null;
	}

	private static String transactionIdOf(RequestMessage request) {
		return request != null ? request.getTransactionId() : null;
	}
}
