package com.wow.be.tc.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 기존 JWT 호출자 전환 스위치 (docs/auth-and-audit-design.md §6.10, §9 7단계).
 *
 * <ul>
 *   <li>꺼짐(기본, 전환 끝) — PLC 요청은 API 키 클라이언트와 ADMIN·OPERATOR 사용자만 할 수 있다.</li>
 *   <li>켜짐(전환 중) — 로그인한 사용자라면 역할과 관계없이 PLC 요청을 허용한다. 아직 API 키로 옮기지 못한 시스템 계정을 위해서다.</li>
 * </ul>
 *
 * <p>배포한 뒤 옮기지 못한 시스템이 드러나도 재기동 없이 되돌릴 수 있게 값을 Redis({@value #KEY})에 둔다
 * ({@code PUT /api/v1/security/dispatch-any-user}). 없으면 설정값 {@code tc.security.dispatch-any-user}(기본 false).
 * Redis 를 읽지 못하면 설정값을 쓴다. 5초 동안 캐시한다(PLC 요청마다 Redis 를 읽지 않게).
 */
@Slf4j
@Component
public class DispatchTransitionOption {

	static final String KEY = "tc:option:dispatch-any-user";

	private static final long CACHE_MS = 5_000;

	private final StringRedisTemplate redis;

	private final boolean defaultValue;

	private volatile boolean cached;

	private volatile long cachedAt;

	public DispatchTransitionOption(RedisConnectionFactory connectionFactory,
			@Value("${tc.security.dispatch-any-user:false}") boolean defaultValue) {
		this.redis = new StringRedisTemplate(connectionFactory);
		this.defaultValue = defaultValue;
	}

	public boolean isOn() {
		long now = System.currentTimeMillis();
		if (now - cachedAt < CACHE_MS) {
			return cached;
		}
		boolean v;
		try {
			String s = redis.opsForValue().get(KEY);
			v = s != null ? Boolean.parseBoolean(s) : defaultValue;
		} catch (RuntimeException e) {
			log.warn("전환 스위치를 Redis 에서 읽지 못해 설정값({})을 씁니다: {}", defaultValue, e.toString());
			v = defaultValue;
		}
		cached = v;
		cachedAt = now;
		return v;
	}

	/** 켜거나 끈다(모든 인스턴스에 최대 5초 안에 반영). null 이면 설정값으로 되돌린다. */
	public void change(Boolean on) {
		if (on == null) {
			redis.delete(KEY);
		} else {
			redis.opsForValue().set(KEY, on.toString());
		}
		cachedAt = 0;
	}
}
