package com.wow.be.tc.apiclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * API 클라이언트의 opcode·pushUrl 허용 범위 (docs/auth-and-audit-design.md §6.4).
 */
class VerifiedApiClientTest {

	private static VerifiedApiClient client(List<String> opcodes, List<String> pushPrefixes) {
		return new VerifiedApiClient("c", "k", opcodes, List.of(), pushPrefixes, 50, 20);
	}

	@Test
	void opcode_정확히_접두_패턴_전체() {
		VerifiedApiClient c = client(List.of("line1.read", "line2.*"), List.of());

		assertThat(c.allowsOpcode("line1.read")).isTrue();
		assertThat(c.allowsOpcode("line1.write")).isFalse();
		assertThat(c.allowsOpcode("line2.anything")).isTrue();
		assertThat(c.allowsOpcode(null)).isFalse();
		assertThat(client(List.of("*"), List.of()).allowsOpcode("x")).isTrue();
	}

	@Test
	void opcode_목록이_비면_아무것도_못_부른다() {
		assertThat(client(List.of(), List.of()).allowsOpcode("line1.read")).isFalse();
	}

	@Test
	void pushUrl_접두어가_없으면_전역_허용_목록에_맡긴다() {
		assertThat(client(List.of("*"), List.of()).allowsPushUrl("http://anywhere/")).isTrue();
	}

	@Test
	void pushUrl_접두어가_있으면_그_안만() {
		VerifiedApiClient c = client(List.of("*"), List.of("http://mes.local/cb/"));

		assertThat(c.allowsPushUrl("http://mes.local/cb/1")).isTrue();
		assertThat(c.allowsPushUrl("http://evil.local/")).isFalse();
		assertThat(c.allowsPushUrl(null)).isFalse();
	}
}
