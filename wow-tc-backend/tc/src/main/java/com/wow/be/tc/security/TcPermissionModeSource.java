package com.wow.be.tc.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.wow.be.framework.security.authz.PermissionModeSource;

import lombok.extern.slf4j.Slf4j;

/**
 * 권한 검사 모드 (docs/auth-and-audit-design.md §4.2). 선언({@code @Requires})이 없는 쓰기 API 를
 * <ul>
 *   <li>AUDIT — 기록만 하고 통과시킨다(framework 권한 감사 집계). 처음에는 이 모드로 두고 누락된 선언을 채운다.</li>
 *   <li>ENFORCE — 403 으로 막는다.</li>
 * </ul>
 * 선언이 있는 API 의 권한 부족은 두 모드 모두 403 이다.
 *
 * <p>값은 Redis({@value #KEY})에 두어 재기동 없이 바꾼다(관리 API). 없으면 설정값 {@code tc.security.permission-mode}(기본 AUDIT).
 * Redis 를 읽지 못하면 설정값을 쓴다.
 */
@Slf4j
@Component
public class TcPermissionModeSource implements PermissionModeSource {

	static final String KEY = "tc:option:permission-mode";

	private final StringRedisTemplate redis;

	private final Mode defaultMode;

	public TcPermissionModeSource(RedisConnectionFactory connectionFactory,
			@Value("${tc.security.permission-mode:AUDIT}") String defaultMode) {
		this.redis = new StringRedisTemplate(connectionFactory);
		this.defaultMode = Mode.valueOf(defaultMode.trim().toUpperCase());
	}

	@Override
	public Mode current() {
		try {
			String v = redis.opsForValue().get(KEY);
			return v != null ? Mode.valueOf(v) : defaultMode;
		} catch (RuntimeException e) {
			log.warn("권한 검사 모드를 Redis 에서 읽지 못해 설정값({})을 씁니다: {}", defaultMode, e.toString());
			return defaultMode;
		}
	}

	/** 모드를 바꾼다(모든 인스턴스에 바로 반영). null 이면 설정값으로 되돌린다. */
	public void change(Mode mode) {
		if (mode == null) {
			redis.delete(KEY);
		} else {
			redis.opsForValue().set(KEY, mode.name());
		}
	}
}
