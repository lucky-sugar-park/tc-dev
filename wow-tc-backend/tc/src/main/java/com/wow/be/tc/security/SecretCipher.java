package com.wow.be.tc.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.wow.be.tc.ToolControlException;

/**
 * DB 에 저장하는 비밀값(외부 DB 비밀번호 등)을 AES-GCM 으로 암호화한다. 해시와 달리 원래 값을 다시 꺼내 써야 하는 값에만 쓴다.
 *
 * <p>키는 {@code tc.secret-key}(환경 변수 {@code TC_SECRET_KEY}, Base64 로 16·24·32바이트). 키가 없어도 tc 는 뜨고,
 * 암호화·복호화가 필요한 기능만 오류를 낸다. 키를 바꾸면 이미 저장한 값은 풀 수 없으므로 다시 입력해야 한다.
 *
 * <p>저장 형식: {@code v1:} + Base64(IV 12바이트 + 암호문·태그).
 */
@Component
public class SecretCipher {

	private static final String PREFIX = "v1:";

	private static final int IV_BYTES = 12;

	private static final int TAG_BITS = 128;

	private static final SecureRandom RANDOM = new SecureRandom();

	private final SecretKeySpec key;

	public SecretCipher(@Value("${tc.secret-key:}") String base64Key) {
		this.key = parseKey(base64Key);
	}

	public boolean isConfigured() {
		return key != null;
	}

	public String encrypt(String plain) throws ToolControlException {
		requireKey();
		try {
			byte[] iv = new byte[IV_BYTES];
			RANDOM.nextBytes(iv);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			byte[] body = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
			byte[] out = new byte[iv.length + body.length];
			System.arraycopy(iv, 0, out, 0, iv.length);
			System.arraycopy(body, 0, out, iv.length, body.length);
			return PREFIX + Base64.getEncoder().encodeToString(out);
		} catch (GeneralSecurityException e) {
			throw ToolControlException.of(ToolControlException.ERR_TC, "비밀값을 암호화하지 못했습니다.");
		}
	}

	public String decrypt(String stored) throws ToolControlException {
		requireKey();
		if (stored == null || !stored.startsWith(PREFIX)) {
			throw ToolControlException.of(ToolControlException.ERR_TC, "저장된 비밀값 형식이 아닙니다.");
		}
		try {
			byte[] in = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
			return new String(cipher.doFinal(in, IV_BYTES, in.length - IV_BYTES), StandardCharsets.UTF_8);
		} catch (GeneralSecurityException | IllegalArgumentException e) {
			// 키가 바뀌었거나 값이 손상됨 — 원인 값은 남기지 않는다
			throw ToolControlException.of(ToolControlException.ERR_TC,
				"저장된 비밀값을 풀 수 없습니다(TC_SECRET_KEY 가 바뀌었으면 비밀번호를 다시 입력하세요).");
		}
	}

	private void requireKey() throws ToolControlException {
		if (key == null) {
			throw ToolControlException.of(ToolControlException.INVALID_REQUEST,
				"TC_SECRET_KEY 가 설정되지 않아 비밀값을 저장·사용할 수 없습니다(운영 문서 참고).");
		}
	}

	private static SecretKeySpec parseKey(String base64Key) {
		if (!StringUtils.hasText(base64Key)) {
			return null;
		}
		byte[] raw;
		try {
			raw = Base64.getDecoder().decode(base64Key.trim());
		} catch (IllegalArgumentException e) {
			throw new IllegalStateException("tc.secret-key(TC_SECRET_KEY) 는 Base64 여야 합니다.");
		}
		if (raw.length != 16 && raw.length != 24 && raw.length != 32) {
			throw new IllegalStateException("tc.secret-key(TC_SECRET_KEY) 는 16·24·32바이트여야 합니다: " + raw.length);
		}
		return new SecretKeySpec(raw, "AES");
	}
}
