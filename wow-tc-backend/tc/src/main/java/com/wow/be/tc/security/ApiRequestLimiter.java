package com.wow.be.tc.security;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.wow.be.tc.apiclient.ApiClientProperties;
import com.wow.be.tc.apiclient.VerifiedApiClient;

import lombok.extern.slf4j.Slf4j;

/**
 * API 클라이언트 요청 제한 (설계 §6.3, §6.8).
 *
 * <ul>
 *   <li>인증 실패 대입 차단 — IP 별 1분 실패 수(Redis). 한도를 넘으면 그 IP 를 일정 시간 막는다.</li>
 *   <li>초당 요청 한도 — 클라이언트별 1초 창 카운터(Redis). 인스턴스가 여럿이어도 합산된다.</li>
 *   <li>동시 처리 한도 — 인스턴스마다 클라이언트별 세마포어(메모리).</li>
 * </ul>
 *
 * <p>Redis 가 응답하지 않으면 Redis 쪽 검사는 통과시키고 경고를 남긴다(fail-open). 제한 기능 때문에 PLC 요청 전체가
 * 멈추는 것이 더 큰 장애이기 때문이다. 동시 처리 한도는 메모리라 Redis 와 무관하다.
 */
@Slf4j
@Component
public class ApiRequestLimiter {

	private static final String FAIL_KEY = "tc:apikey-fail:";

	private static final String BLOCK_KEY = "tc:apikey-block:";

	private static final String QUOTA_KEY = "tc:quota:";

	private final StringRedisTemplate redis;

	private final ApiClientProperties properties;

	private final Map<String, LimitedSemaphore> semaphores = new ConcurrentHashMap<>();

	/** 한도가 바뀌면 새 세마포어로 바꾼다. 처리 중인 요청은 받은 객체에 돌려준다. */
	public record LimitedSemaphore(int limit, Semaphore semaphore) {
	}

	public ApiRequestLimiter(RedisConnectionFactory connectionFactory, ApiClientProperties properties) {
		this.redis = new StringRedisTemplate(connectionFactory);
		this.properties = properties;
	}

	/** 이 IP 가 인증 실패로 막혀 있는가 */
	public boolean isBlocked(String ip) {
		try {
			return Boolean.TRUE.equals(redis.hasKey(BLOCK_KEY + ip));
		} catch (RuntimeException e) {
			log.warn("API 키 차단 여부를 확인하지 못했습니다(통과시킴): {}", e.toString());
			return false;
		}
	}

	/** 인증 실패 한 번. 1분 실패 수가 한도를 넘으면 그 IP 를 막는다. */
	public void recordFailure(String ip) {
		try {
			Long count = redis.opsForValue().increment(FAIL_KEY + ip);
			if (count != null && count == 1L) {
				redis.expire(FAIL_KEY + ip, Duration.ofMinutes(1));
			}
			if (count != null && count > properties.getFailLimitPerMinute()) {
				redis.opsForValue().set(BLOCK_KEY + ip, "1", properties.getFailBlock());
				log.warn("API 키 인증 실패가 1분에 {}회를 넘어 IP 를 {} 동안 막습니다: {}",
					properties.getFailLimitPerMinute(), properties.getFailBlock(), ip);
			}
		} catch (RuntimeException e) {
			log.warn("API 키 인증 실패를 세지 못했습니다: {}", e.toString());
		}
	}

	/** 초당 요청 한도 안인가. 이번 요청을 센다. */
	public boolean tryRate(VerifiedApiClient client) {
		String key = QUOTA_KEY + client.clientId() + ":" + (System.currentTimeMillis() / 1000);
		try {
			Long count = redis.opsForValue().increment(key);
			if (count != null && count == 1L) {
				redis.expire(key, Duration.ofSeconds(2));
			}
			return count == null || count <= client.rateLimitPerSec();
		} catch (RuntimeException e) {
			log.warn("초당 요청 한도를 확인하지 못했습니다(통과시킴): client={}, {}", client.clientId(), e.toString());
			return true;
		}
	}

	/** 동시 처리 자리를 하나 얻는다. 못 얻으면 null. 얻었으면 처리 뒤 반드시 {@code release()} 한다. */
	public Semaphore tryAcquireConcurrency(VerifiedApiClient client) {
		LimitedSemaphore ls = semaphores.compute(client.clientId(), (id, current) ->
			current != null && current.limit() == client.maxConcurrent()
				? current
				: new LimitedSemaphore(client.maxConcurrent(), new Semaphore(client.maxConcurrent())));
		Semaphore s = ls.semaphore();
		return s.tryAcquire() ? s : null;
	}
}
