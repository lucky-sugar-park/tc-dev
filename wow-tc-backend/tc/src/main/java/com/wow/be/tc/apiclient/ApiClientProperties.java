package com.wow.be.tc.apiclient;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * API 클라이언트·키 설정 ({@code tc.api-client.*}). 클라이언트별 값(테이블)이 없을 때 쓰는 기본값들이다.
 * 이 값들은 기동할 때 읽으므로 바꾸려면 재기동해야 한다 — 클라이언트별 값은 재기동 없이 바뀐다.
 *
 * <pre>
 * tc:
 *   api-client:
 *     default-rate-limit-per-sec: 50
 *     default-max-concurrent: 20
 *     default-key-days: 365
 *     max-key-days: 730
 *     cache-ttl: 30s
 *     fail-limit-per-minute: 30
 *     fail-block: 5m
 *     expiry-notice-days: [30, 7, 1]
 *     unused-days: 90
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "tc.api-client")
public class ApiClientProperties {

	/** 클라이언트에 RATE_LIMIT_PER_SEC 가 없을 때의 초당 요청 한도 (결정 D8) */
	private int defaultRateLimitPerSec = 50;

	/** 클라이언트에 MAX_CONCURRENT 가 없을 때의 동시 처리 한도 (결정 D8) */
	private int defaultMaxConcurrent = 20;

	/** 발급할 때 수명을 주지 않으면 쓰는 키 수명(일) (결정 D9) */
	private int defaultKeyDays = 365;

	/** 키 수명 상한(일) */
	private int maxKeyDays = 730;

	/** 키 검증 결과 캐시 시간. 같은 인스턴스의 변경은 즉시 지우므로, 여러 인스턴스일 때 다른 인스턴스에 반영되는 최대 지연이다. */
	private Duration cacheTtl = Duration.ofSeconds(30);

	/** 한 IP 에서 1분 동안 인증 실패가 이 횟수를 넘으면 차단한다 */
	private int failLimitPerMinute = 30;

	/** 인증 실패 차단 시간 */
	private Duration failBlock = Duration.ofMinutes(5);

	/** 만료 임박 알림을 보낼 남은 일수 */
	private int[] expiryNoticeDays = { 30, 7, 1 };

	/** 활성 키가 이 일수 넘게 쓰이지 않으면 "미사용"으로 표시한다 */
	private int unusedDays = 90;
}
