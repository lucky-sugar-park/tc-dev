package com.wow.be.tc.endpoints;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wow.be.framework.security.authz.PublicApi;
import com.wow.be.tc.plc.connector.PlcAdapterManager;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * 로드밸런서 헬스 체크 (docs/plc-ha-design.md §7, §10 1단계). 인증 없이 부른다(TcSecurityConfig 의 healthChain).
 *
 * <ul>
 *   <li>DB 에 닿지 않으면 503 — 이 인스턴스는 거의 아무 일도 할 수 없다.</li>
 *   <li>Redis 에 닿지 않으면 200 + {@code DEGRADED} — API 키 PLC 요청은 계속 처리한다(제한 기능 fail-open).
 *       다만 로그인·토큰 재발급은 되지 않으므로 상태로 알린다.</li>
 * </ul>
 * 각 확인은 2초 안에 끝나지 않으면 DOWN 으로 본다(헬스 체크가 매달리지 않게).
 */
@Tag(name = "Health", description = "로드밸런서 헬스 체크 — 인증 없음")
@RestController
@RequiredArgsConstructor
@PublicApi
public class HealthController {

	private static final long CHECK_TIMEOUT_MS = 2000;

	private final DataSource dataSource;

	private final RedisConnectionFactory redisConnectionFactory;

	@Operation(summary = "헬스 체크", description = "UP(200) / DEGRADED(200, Redis 없음) / DOWN(503, DB 없음)")
	@GetMapping("/api/health")
	public ResponseEntity<Map<String, Object>> health() {
		boolean db = check(() -> {
			try (Connection c = dataSource.getConnection()) {
				return c.isValid(2);
			}
		});
		boolean redis = check(() -> {
			try (RedisConnection c = redisConnectionFactory.getConnection()) {
				return "PONG".equalsIgnoreCase(c.ping());
			}
		});

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("status", !db ? "DOWN" : redis ? "UP" : "DEGRADED");
		body.put("db", db ? "UP" : "DOWN");
		body.put("redis", redis ? "UP" : "DOWN");
		body.put("plcAdapters", PlcAdapterManager.getPlcManager().size());
		return ResponseEntity.status(db ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
	}

	@FunctionalInterface
	private interface Probe {
		boolean run() throws Exception;
	}

	private static boolean check(Probe probe) {
		try {
			return CompletableFuture.supplyAsync(() -> {
				try {
					return probe.run();
				} catch (Exception e) {
					return false;
				}
			}).get(CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
		} catch (Exception e) {
			return false;
		}
	}
}
