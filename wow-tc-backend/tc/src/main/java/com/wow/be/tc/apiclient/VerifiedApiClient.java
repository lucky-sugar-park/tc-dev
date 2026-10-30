package com.wow.be.tc.apiclient;

import java.util.List;

/**
 * API 키 검증에 성공한 호출자. 요청을 처리하는 동안 허용 범위(opcode·IP·pushUrl)와 사용량 한도를 판단하는 데 쓴다.
 *
 * @param rateLimitPerSec 전역 기본값을 이미 적용한 값
 * @param maxConcurrent   전역 기본값을 이미 적용한 값
 */
public record VerifiedApiClient(
		String clientId,
		String keyId,
		List<String> allowedOpcodes,
		List<String> allowedIps,
		List<String> allowedPushUrlPrefixes,
		int rateLimitPerSec,
		int maxConcurrent) {

	/** opcode 를 부를 수 있는가. {@code *} 전체, 끝이 {@code *} 이면 접두 패턴, 그 밖에는 정확히 같아야 한다. */
	public boolean allowsOpcode(String opcode) {
		if (opcode == null) {
			return false;
		}
		for (String pattern : allowedOpcodes) {
			if ("*".equals(pattern)) {
				return true;
			}
			if (pattern.endsWith("*") ? opcode.startsWith(pattern.substring(0, pattern.length() - 1)) : opcode.equals(pattern)) {
				return true;
			}
		}
		return false;
	}

	/** pushUrl 을 쓸 수 있는가. 클라이언트에 접두어가 없으면 전역 허용 목록에 맡긴다(true). */
	public boolean allowsPushUrl(String url) {
		if (allowedPushUrlPrefixes.isEmpty()) {
			return true;
		}
		return url != null && allowedPushUrlPrefixes.stream().anyMatch(url::startsWith);
	}
}
