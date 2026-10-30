package com.wow.be.framework.dispatch;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

import org.slf4j.MDC;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 디스패치 기반 설정 — 설정값({@link DispatchProperties})과 비동기 디스패치 스레드 풀을 등록한다.
 *
 * <p>커맨드 리졸버({@link AnnotatedCommandResolver}, {@link ConfiguredCommandResolver})와
 * {@link MessageDispatcher} 는 각자 {@code @Component} 로 올라온다. 앱이 {@code com.wow.be.framework.dispatch}
 * 패키지를 컴포넌트 스캔해야 한다.
 *
 * <p>스레드 풀 크기는 {@code app.dispatch.executor.*} 로 바꾼다. 같은 이름의 빈을 앱에서 다시 정의하는 방식은
 * Spring Boot 의 빈 재정의 금지 때문에 쓰지 않는다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DispatchProperties.class)
public class DispatchConfig {

	/** {@link MessageDispatcher} 가 주입받는 스레드 풀 빈 이름 */
	public static final String DISPATCH_TASK_EXECUTOR = "dispatchTaskExecutor";

	@Bean(name = DISPATCH_TASK_EXECUTOR)
	public ThreadPoolTaskExecutor dispatchTaskExecutor(DispatchProperties properties) {
		DispatchProperties.ExecutorProperties props = properties.getExecutor();

		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(props.getCoreSize());
		executor.setMaxPoolSize(props.getMaxSize());
		executor.setQueueCapacity(props.getQueueCapacity());
		executor.setThreadNamePrefix(props.getThreadNamePrefix());
		// 큐가 가득 차면 거절한다(TaskRejectedException). 호출 스레드에서 대신 실행하면 HTTP 요청 스레드가 PLC 응답을 기다리며 묶인다.
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
		executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
		executor.setWaitForTasksToCompleteOnShutdown(!props.getAwaitTermination().isZero());
		executor.setAwaitTerminationMillis(props.getAwaitTermination().toMillis());
		return executor;
	}

	/**
	 * 호출 스레드의 인증 정보(SecurityContext)와 MDC 를 작업 스레드로 복사한다.
	 * 비동기 커맨드 안에서도 PersistentEntity 의 작성자 필드가 채워지고, 로그를 같은 요청으로 묶어 볼 수 있다.
	 * 작업이 끝나면 작업 스레드의 컨텍스트를 비운다 — 풀 스레드가 다음 작업에 이전 사용자를 넘기지 않게 한다.
	 */
	static final class ContextPropagatingTaskDecorator implements TaskDecorator {

		@Override
		public Runnable decorate(Runnable runnable) {
			SecurityContext callerContext = SecurityContextHolder.getContext();
			SecurityContext copied = SecurityContextHolder.createEmptyContext();
			copied.setAuthentication(callerContext.getAuthentication());
			Map<String, String> callerMdc = MDC.getCopyOfContextMap();

			return () -> {
				SecurityContextHolder.setContext(copied);
				if (callerMdc != null) MDC.setContextMap(callerMdc);
				else MDC.clear();
				try {
					runnable.run();
				} finally {
					SecurityContextHolder.clearContext();
					MDC.clear();
				}
			};
		}
	}
}
