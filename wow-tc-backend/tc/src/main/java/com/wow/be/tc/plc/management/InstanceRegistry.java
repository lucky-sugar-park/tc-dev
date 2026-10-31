package com.wow.be.tc.plc.management;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 살아 있는 tc 인스턴스 목록 — {@code tc:instance:{instanceId}} = 내부 주소, 만료는 임대와 같다(docs/plc-ha-design.md §3.2).
 * 소유권 관리자가 임대를 갱신할 때 함께 갱신하고, 균형 규칙(§3.3)의 인스턴스 수를 여기서 센다.
 * Redis 오류는 부르는 쪽(소유권 관리자)이 처리한다.
 */
@Component
public class InstanceRegistry {

	static final String KEY = "tc:instance:";

	private final StringRedisTemplate redis;

	private final InstanceIdentity identity;

	/** 다른 인스턴스가 PLC 교환을 넘길 때 쓸 이 인스턴스의 주소(다중화 4단계). 비어 있어도 된다 */
	private final String internalUrl;

	public InstanceRegistry(RedisConnectionFactory connectionFactory, InstanceIdentity identity,
			@Value("${tc.instance.internal-url:}") String internalUrl) {
		this.redis = new StringRedisTemplate(connectionFactory);
		this.identity = identity;
		this.internalUrl = internalUrl;
	}

	public void heartbeat(Duration ttl) {
		redis.opsForValue().set(KEY + identity.id(), internalUrl.isBlank() ? "-" : internalUrl, ttl);
	}

	public boolean hasInternalUrl() {
		return !internalUrl.isBlank();
	}

	/** 그 인스턴스의 내부 주소. 목록에 없거나(죽음) 주소를 두지 않았으면 null */
	public String urlOf(String instanceId) {
		String url = redis.opsForValue().get(KEY + instanceId);
		return url == null || url.isBlank() || "-".equals(url) ? null : url;
	}

	/** 살아 있는 인스턴스 수(자신 포함, 최소 1) */
	public int liveCount() {
		int count = 0;
		try (Cursor<String> keys = redis.scan(ScanOptions.scanOptions().match(KEY + "*").count(100).build())) {
			while (keys.hasNext()) {
				keys.next();
				count++;
			}
		}
		return Math.max(count, 1);
	}

	public void remove() {
		redis.delete(KEY + identity.id());
	}
}
