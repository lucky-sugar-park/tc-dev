package com.wow.be.tc.plc.remote;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 인스턴스 사이 PLC 교환 요청의 서명(docs/plc-ha-design.md §4.1).
 *
 * <p>{@code HMAC-SHA256(비밀값, plcName + "\n" + timestamp + "\n" + hex(sha256(본문)))} 을 Base64 로. 비밀값은 모든 인스턴스가
 * 같은 {@code TC_INTERNAL_SECRET} 을 쓴다. 바꿀 때는 순차 재기동 동안 옛 값을 {@code TC_INTERNAL_SECRET_PREVIOUS} 로 함께 받는다
 * (보내는 쪽은 늘 지금 값으로 서명한다). 시각이 ±30초 밖이면 받지 않는다(가로챈 요청 재사용 방지).
 */
@Component
public class InternalSignature {

	public static final String HEADER_INSTANCE = "X-TC-Instance";

	public static final String HEADER_TIMESTAMP = "X-TC-Timestamp";

	public static final String HEADER_SIGNATURE = "X-TC-Signature";

	static final long MAX_SKEW_MS = 30_000;

	private final byte[] secret;

	private final byte[] previous;

	public InternalSignature(@Value("${tc.internal.secret:}") String secret,
			@Value("${tc.internal.secret-previous:}") String previous) {
		this.secret = secret.getBytes(StandardCharsets.UTF_8);
		this.previous = previous.getBytes(StandardCharsets.UTF_8);
	}

	public boolean isConfigured() {
		return secret.length > 0;
	}

	public String sign(String plcName, long timestamp, byte[] body) {
		return sign(secret, plcName, timestamp, body);
	}

	/** 서명과 시각이 맞는가. 비밀값이 없으면 늘 false */
	public boolean verify(String plcName, String timestamp, String signature, byte[] body) {
		if (!isConfigured() || timestamp == null || signature == null) {
			return false;
		}
		long ts;
		try {
			ts = Long.parseLong(timestamp);
		} catch (NumberFormatException e) {
			return false;
		}
		if (Math.abs(System.currentTimeMillis() - ts) > MAX_SKEW_MS) {
			return false;
		}
		byte[] given = signature.getBytes(StandardCharsets.UTF_8);
		if (MessageDigest.isEqual(given, sign(secret, plcName, ts, body).getBytes(StandardCharsets.UTF_8))) {
			return true;
		}
		return previous.length > 0
			&& MessageDigest.isEqual(given, sign(previous, plcName, ts, body).getBytes(StandardCharsets.UTF_8));
	}

	/** 비밀값이 없으면 빈 서명 — 받는 쪽은 비밀값이 없으면 늘 거절한다 */
	static String sign(byte[] key, String plcName, long timestamp, byte[] body) {
		if (key.length == 0) {
			return "";
		}
		try {
			String bodyHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(key, "HmacSHA256"));
			byte[] sig = mac.doFinal((plcName + "\n" + timestamp + "\n" + bodyHash).getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(sig);
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException("내부 요청 서명을 만들 수 없습니다", e);
		}
	}
}
