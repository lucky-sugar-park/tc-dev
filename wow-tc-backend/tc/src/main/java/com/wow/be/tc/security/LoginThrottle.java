package com.wow.be.tc.security;

import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 로그인 실패 제한 (docs/auth-and-audit-design.md §3.4). 계정을 잠그지 않는다 — 사용자가 적어 남이 일부러 틀려 유일한
 * 관리자를 잠그는 공격 수단이 될 수 있어서다. 대신 아이디별·IP별 실패 수를 세어 한도를 넘으면 잠시 로그인을 받지 않는다(429).
 *
 * <p>IP 한도는 아이디 한도보다 크게 둔다 — 같은 사무실(NAT)·프록시 뒤의 사용자들은 IP 하나를 나눠 쓰므로, 한 사람이 몇 번 틀렸다고
 * 모두가 막히지 않게 한다.
 *
 * <p>Redis 가 응답하지 않으면 통과시킨다(fail-open) — 제한 기능 때문에 로그인 자체가 막히지 않게.
 */
@Slf4j
@Component
public class LoginThrottle {

	private static final String FAIL = "tc:login-fail:";

	private static final String BLOCK = "tc:login-block:";

	private final StringRedisTemplate redis;

	private final int maxFailures;

	private final int maxIpFailures;

	private final Duration window;

	public LoginThrottle(RedisConnectionFactory connectionFactory,
			@Value("${tc.security.login-throttle.max-failures:5}") int maxFailures,
			@Value("${tc.security.login-throttle.max-ip-failures:20}") int maxIpFailures,
			@Value("${tc.security.login-throttle.window:15m}") Duration window) {
		this.redis = new StringRedisTemplate(connectionFactory);
		this.maxFailures = maxFailures;
		this.maxIpFailures = maxIpFailures;
		this.window = window;
	}

	public boolean isBlocked(String userId, String ip) {
		try {
			return Boolean.TRUE.equals(redis.hasKey(BLOCK + "user:" + userId)) || Boolean.TRUE.equals(redis.hasKey(BLOCK + "ip:" + ip));
		} catch (RuntimeException e) {
			log.warn("로그인 제한 여부를 확인하지 못했습니다(통과시킴): {}", e.toString());
			return false;
		}
	}

	/** 지금 로그인을 받지 않는 아이디들(아이디 한도로 막힌 것만 — IP 로 막힌 것은 빠진다) */
	public Set<String> blockedUsers() {
		try {
			Set<String> keys = redis.keys(BLOCK + "user:*");
			return keys == null ? Set.of() : keys.stream().map(k -> k.substring((BLOCK + "user:").length())).collect(Collectors.toSet());
		} catch (RuntimeException e) {
			log.warn("로그인 제한 목록을 읽지 못했습니다: {}", e.toString());
			return Set.of();
		}
	}

	/** 관리자가 아이디의 로그인 제한을 푼다 — 실패 수도 지운다. 막혀 있었으면 true */
	public boolean release(String userId) {
		Boolean blocked = redis.delete(BLOCK + "user:" + userId);
		redis.delete(FAIL + "user:" + userId);
		return Boolean.TRUE.equals(blocked);
	}

	public void recordFailure(String userId, String ip) {
		count("user:" + userId, maxFailures);
		count("ip:" + ip, maxIpFailures);
	}

	public void recordSuccess(String userId) {
		try {
			redis.delete(FAIL + "user:" + userId);
		} catch (RuntimeException e) {
			log.warn("로그인 실패 수를 지우지 못했습니다: {}", e.toString());
		}
	}

	private void count(String key, int limit) {
		try {
			Long n = redis.opsForValue().increment(FAIL + key);
			if (n != null && n == 1L) {
				redis.expire(FAIL + key, window);
			}
			if (n != null && n >= limit) {
				redis.opsForValue().set(BLOCK + key, "1", window);
				log.warn("로그인 실패가 {} 안에 {}회라 {} 동안 로그인을 받지 않습니다: {}", window, n, window, key);
			}
		} catch (RuntimeException e) {
			log.warn("로그인 실패를 세지 못했습니다: {}", e.toString());
		}
	}
}
