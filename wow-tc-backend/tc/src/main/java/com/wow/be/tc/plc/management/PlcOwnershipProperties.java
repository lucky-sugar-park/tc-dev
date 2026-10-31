package com.wow.be.tc.plc.management;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * PLC 소유권(다중화) 설정 ({@code tc.plc.ownership.*}, docs/plc-ha-design.md §3.2·§3.3, 결정 H1).
 *
 * <pre>
 * tc:
 *   plc:
 *     ownership:
 *       enabled: false            # true 면 Redis 임대로 PLC 마다 연결할 인스턴스를 하나 정한다
 *       lease-ttl: 10s
 *       renew-interval: 3s
 *       scan-interval: 2s
 *       takeover-fast-retry: 30s  # 임대를 얻은 뒤 이 시간 동안은 재연결을 짧은 고정 간격으로
 *       takeover-retry-interval: 2s
 * </pre>
 *
 * <p>꺼져 있으면(기본) 지금과 같다 — 기동 때 게시된 커넥터를 모두 연결하고, Redis 없이도 PLC 요청을 처리한다.
 * 켜면 PLC 연결이 Redis 임대에 달린다 — 기동 때 Redis 가 없으면 아무 PLC 에도 연결하지 않는다(이미 가진 연결은 유지).
 */
@Data
@ConfigurationProperties(prefix = "tc.plc.ownership")
public class PlcOwnershipProperties {

	private boolean enabled;

	private Duration leaseTtl = Duration.ofSeconds(10);

	private Duration renewInterval = Duration.ofSeconds(3);

	private Duration scanInterval = Duration.ofSeconds(2);

	private Duration takeoverFastRetry = Duration.ofSeconds(30);

	private Duration takeoverRetryInterval = Duration.ofSeconds(2);
}
