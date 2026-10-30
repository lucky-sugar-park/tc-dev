package com.wow.be.tc.apiclient.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * API 키 문자열 {@code tck_<keyId>_<secret>} 을 만들고 나누고 해시한다 (설계 §6.2).
 *
 * <ul>
 *   <li>keyId — 무작위 12자(base32 소문자). 조회용 공개 부분.</li>
 *   <li>secret — 32바이트 무작위값(base64url, 패딩 없음 43자).</li>
 *   <li>해시는 SHA-256 이다. 키가 256비트 무작위값이라 사전 대입이 의미 없고, 요청마다 검증하므로 BCrypt 처럼 느린 해시는 맞지 않다.</li>
 * </ul>
 */
final class ApiKeyCodec {

	static final String PREFIX = "tck_";

	private static final String BASE32 = "abcdefghijklmnopqrstuvwxyz234567";

	private static final Pattern FORMAT = Pattern.compile("tck_([a-z2-7]{12})_([A-Za-z0-9_-]{43})");

	private static final SecureRandom RANDOM = new SecureRandom();

	private ApiKeyCodec() {
	}

	record Parts(String keyId, String secret) {
	}

	record Generated(String keyId, String apiKey, String secretHash) {
	}

	static Generated generate() {
		StringBuilder keyId = new StringBuilder(12);
		for (int i = 0; i < 12; i++) {
			keyId.append(BASE32.charAt(RANDOM.nextInt(BASE32.length())));
		}
		byte[] secretBytes = new byte[32];
		RANDOM.nextBytes(secretBytes);
		String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
		return new Generated(keyId.toString(), PREFIX + keyId + "_" + secret, hash(secret));
	}

	/** 형식이 맞으면 나눈 값, 아니면 null */
	static Parts parse(String apiKey) {
		if (apiKey == null) {
			return null;
		}
		Matcher m = FORMAT.matcher(apiKey.trim());
		return m.matches() ? new Parts(m.group(1), m.group(2)) : null;
	}

	static String hash(String secret) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 을 쓸 수 없습니다", e);
		}
	}

	/** 상수 시간 비교 — 응답 시간으로 해시 앞부분을 떠보지 못하게 한다 */
	static boolean matches(String secret, String expectedHash) {
		byte[] actual = hash(secret).getBytes(StandardCharsets.US_ASCII);
		byte[] expected = expectedHash.getBytes(StandardCharsets.US_ASCII);
		return MessageDigest.isEqual(actual, expected);
	}
}
