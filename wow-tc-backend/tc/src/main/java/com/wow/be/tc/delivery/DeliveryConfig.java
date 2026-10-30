package com.wow.be.tc.delivery;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

/**
 * 결과 전달용 스레드 풀과 HTTP 클라이언트.
 *
 * <p>레거시는 전달할 때마다 {@code Executors.newCachedThreadPool()} 를 새로 만들고(스레드 누수),
 * 타임아웃 없는 {@code RestTemplate} 을 썼다. 상대 서버가 응답하지 않으면 스레드가 끝없이 묶였다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DeliveryProperties.class)
public class DeliveryConfig {

	public static final String DELIVERY_TASK_EXECUTOR = "deliveryTaskExecutor";

	public static final String DELIVERY_REST_CLIENT = "deliveryRestClient";

	@Bean(name = DELIVERY_TASK_EXECUTOR)
	public ThreadPoolTaskExecutor deliveryTaskExecutor(DeliveryProperties properties) {
		DeliveryProperties.ExecutorProperties props = properties.getExecutor();
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(props.getCoreSize());
		executor.setMaxPoolSize(props.getMaxSize());
		executor.setQueueCapacity(props.getQueueCapacity());
		executor.setThreadNamePrefix(props.getThreadNamePrefix());
		// 큐가 가득 차면 거절한다 — 거절된 전달은 ResultDeliveryService 가 실패로 기록한다
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(30);
		return executor;
	}

	@Bean(name = DELIVERY_REST_CLIENT)
	public RestClient deliveryRestClient(DeliveryProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.getConnectTimeout());
		requestFactory.setReadTimeout(properties.getReadTimeout());
		// 본문을 버퍼링해 Content-Length 를 붙인다 — 그대로면 chunked 로 보내져, chunked 를 못 읽는 수신 서버는 빈 본문을 받는다
		return RestClient.builder().requestFactory(new BufferingClientHttpRequestFactory(requestFactory)).build();
	}
}
