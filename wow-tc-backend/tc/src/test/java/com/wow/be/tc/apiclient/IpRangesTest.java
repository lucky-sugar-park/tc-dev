package com.wow.be.tc.apiclient;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * API 클라이언트 허용 IP·CIDR 검사 (docs/auth-and-audit-design.md §6.2).
 */
class IpRangesTest {

	@Test
	void 목록이_비면_모두_허용() {
		assertThat(IpRanges.contains(List.of(), "10.0.0.1")).isTrue();
		assertThat(IpRanges.contains(null, "10.0.0.1")).isTrue();
	}

	@Test
	void IPv4_주소와_CIDR() {
		List<String> ranges = List.of("10.0.1.15", "192.168.0.0/24");

		assertThat(IpRanges.contains(ranges, "10.0.1.15")).isTrue();
		assertThat(IpRanges.contains(ranges, "10.0.1.16")).isFalse();
		assertThat(IpRanges.contains(ranges, "192.168.0.200")).isTrue();
		assertThat(IpRanges.contains(ranges, "192.168.1.1")).isFalse();
	}

	@Test
	void 바이트_경계가_아닌_접두_길이() {
		List<String> ranges = List.of("10.0.0.0/20");

		assertThat(IpRanges.contains(ranges, "10.0.15.255")).isTrue();
		assertThat(IpRanges.contains(ranges, "10.0.16.0")).isFalse();
	}

	@Test
	void IPv6_와_IPv4_매핑_주소() {
		assertThat(IpRanges.contains(List.of("fd00::/8"), "fd12:3456::1")).isTrue();
		assertThat(IpRanges.contains(List.of("fd00::/8"), "fe80::1")).isFalse();
		assertThat(IpRanges.contains(List.of("127.0.0.0/8"), "::ffff:127.0.0.1")).isTrue();
	}

	@Test
	void 이름은_받지_않는다_DNS_조회_없음() {
		assertThat(IpRanges.isValid("localhost")).isFalse();
		assertThat(IpRanges.contains(List.of("127.0.0.1"), "localhost")).isFalse();
	}

	@Test
	void 형식_검사() {
		assertThat(IpRanges.isValid("10.0.0.0/24")).isTrue();
		assertThat(IpRanges.isValid("10.0.0.1")).isTrue();
		assertThat(IpRanges.isValid("10.0.0.0/33")).isFalse();
		assertThat(IpRanges.isValid("10.0.0.0/x")).isFalse();
		assertThat(IpRanges.isValid("")).isFalse();
		assertThat(IpRanges.isValid("::1/128")).isTrue();
	}
}
