package com.wow.be.tc.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;

import com.wow.be.tc.ToolControlException;

/**
 * 저장용 비밀값 암호화 (docs/db-table-delivery.md §4).
 */
class SecretCipherTest {

	private static String key(int fill) {
		byte[] raw = new byte[32];
		java.util.Arrays.fill(raw, (byte) fill);
		return Base64.getEncoder().encodeToString(raw);
	}

	@Test
	void 암호화한_값을_다시_푼다() throws Exception {
		SecretCipher cipher = new SecretCipher(key(1));
		String stored = cipher.encrypt("비밀-p@ss");

		assertThat(stored).startsWith("v1:").doesNotContain("p@ss");
		assertThat(cipher.decrypt(stored)).isEqualTo("비밀-p@ss");
	}

	@Test
	void 같은_값도_매번_다르게_저장된다() throws Exception {
		SecretCipher cipher = new SecretCipher(key(1));

		assertThat(cipher.encrypt("same")).isNotEqualTo(cipher.encrypt("same"));
	}

	@Test
	void 다른_키나_손상된_값은_풀지_못한다() throws Exception {
		String stored = new SecretCipher(key(1)).encrypt("x");

		assertThatThrownBy(() -> new SecretCipher(key(2)).decrypt(stored)).isInstanceOf(ToolControlException.class);
		assertThatThrownBy(() -> new SecretCipher(key(1)).decrypt(stored.substring(0, stored.length() - 4) + "AAAA"))
			.isInstanceOf(ToolControlException.class);
	}

	@Test
	void 키가_없으면_뜨지만_쓰지_못한다() {
		SecretCipher cipher = new SecretCipher("");

		assertThat(cipher.isConfigured()).isFalse();
		assertThatThrownBy(() -> cipher.encrypt("x")).isInstanceOf(ToolControlException.class)
			.hasMessageContaining("TC_SECRET_KEY");
	}

	@Test
	void 키_길이가_틀리면_기동_실패() {
		assertThatThrownBy(() -> new SecretCipher(Base64.getEncoder().encodeToString(new byte[10])))
			.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new SecretCipher("not base64!")).isInstanceOf(IllegalStateException.class);
	}
}
