package com.wow.be.tc.delivery;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * 결과 전달 설정 ({@code tc.delivery.*}).
 *
 * <pre>
 * tc:
 *   delivery:
 *     connect-timeout: 3s
 *     read-timeout: 10s
 *     allowed-url-prefixes:          # 비어 있으면 모든 주소 허용(경고 로그)
 *       - http://mes.local/
 *     executor:
 *       core-size: 4
 *       max-size: 16
 *       queue-capacity: 500
 *     redelivery-max-batch: 500
 *     redelivery-duplicate-window: 10s
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "tc.delivery")
public class DeliveryProperties {

	private Duration connectTimeout = Duration.ofSeconds(3);

	private Duration readTimeout = Duration.ofSeconds(10);

	/**
	 * 결과를 보내도 되는 주소의 앞부분 목록. 요청이 지정한 callback 주소와 WebHook 주소 모두 검사한다.
	 * 비어 있으면 모든 주소를 허용한다 — 레거시와 같지만 SSRF 위험이 있으므로 운영에서는 채운다.
	 */
	private List<String> allowedUrlPrefixes = new ArrayList<>();

	private ExecutorProperties executor = new ExecutorProperties();

	/** 관리자 재전송 한 번에 보낼 수 있는 건수(대상 × 기록) — docs/redelivery-design.md R8 */
	private int redeliveryMaxBatch = 500;

	/** 같은 원본·같은 대상 재전송을 이 시간 안에 다시 요청하면 건너뛴다(중복 클릭) — R11 */
	private Duration redeliveryDuplicateWindow = Duration.ofSeconds(10);

	@Data
	public static class ExecutorProperties {

		private int coreSize = 4;

		private int maxSize = 16;

		private int queueCapacity = 500;

		private String threadNamePrefix = "delivery-";
	}
}
