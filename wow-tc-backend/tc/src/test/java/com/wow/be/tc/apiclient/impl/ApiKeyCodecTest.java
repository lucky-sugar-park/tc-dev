package com.wow.be.tc.apiclient.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * API 키 형식·해시 (docs/auth-and-audit-design.md §6.2). 인프라 없이 도는 단위 테스트.
 */
class ApiKeyCodecTest {

	@Test
	void 발급한_키는_나뉘고_해시가_맞는다() {
		ApiKeyCodec.Generated g = ApiKeyCodec.generate();

		ApiKeyCodec.Parts parts = ApiKeyCodec.parse(g.apiKey());

		assertThat(g.apiKey()).startsWith("tck_");
		assertThat(parts).isNotNull();
		assertThat(parts.keyId()).isEqualTo(g.keyId()).hasSize(12);
		assertThat(ApiKeyCodec.matches(parts.secret(), g.secretHash())).isTrue();
		assertThat(g.secretHash()).hasSize(64);
	}

	@Test
	void 비밀_부분이_다르면_해시가_맞지_않는다() {
		ApiKeyCodec.Generated g = ApiKeyCodec.generate();
		ApiKeyCodec.Generated other = ApiKeyCodec.generate();

		assertThat(ApiKeyCodec.matches(ApiKeyCodec.parse(other.apiKey()).secret(), g.secretHash())).isFalse();
	}

	@Test
	void 형식이_틀리면_null() {
		assertThat(ApiKeyCodec.parse(null)).isNull();
		assertThat(ApiKeyCodec.parse("not-a-key")).isNull();
		assertThat(ApiKeyCodec.parse("tck_ABCDEFGHIJKL_" + "A".repeat(43))).as("keyId 는 소문자 base32").isNull();
		assertThat(ApiKeyCodec.parse("tck_abcdefghijkl_" + "A".repeat(42))).as("secret 은 43자").isNull();
	}

	@Test
	void 앞뒤_공백은_무시한다() {
		ApiKeyCodec.Generated g = ApiKeyCodec.generate();

		assertThat(ApiKeyCodec.parse("  " + g.apiKey() + "\n")).isNotNull();
	}

	@Test
	void 발급할_때마다_다른_키() {
		assertThat(ApiKeyCodec.generate().apiKey()).isNotEqualTo(ApiKeyCodec.generate().apiKey());
	}
}
