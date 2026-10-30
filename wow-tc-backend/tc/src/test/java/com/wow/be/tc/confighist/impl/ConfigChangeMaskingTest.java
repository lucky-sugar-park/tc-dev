package com.wow.be.tc.confighist.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 설정 변경 이력 JSON 의 비밀값 가리기 (docs/auth-and-audit-design.md §5.2).
 */
class ConfigChangeMaskingTest {

	@Test
	void 비밀값_필드는_대소문자_무시하고_가린다() {
		String json = DefaultConfigChangeService.toMaskedJson(Map.of(
			"id", "u1",
			"password", "$2a$10$abc",
			"keyHash", "deadbeef",
			"ApiKey", "tck_x"));

		assertThat(json).contains("\"id\":\"u1\"")
			.contains("\"password\":\"***\"")
			.contains("\"keyHash\":\"***\"")
			.contains("\"ApiKey\":\"***\"")
			.doesNotContain("$2a$").doesNotContain("deadbeef").doesNotContain("tck_x");
	}

	@Test
	void 중첩된_객체와_목록_안까지_가린다() {
		String json = DefaultConfigChangeService.toMaskedJson(Map.of(
			"users", List.of(Map.of("id", "a", "password", "p1"), Map.of("id", "b", "secret", "s1"))));

		assertThat(json).doesNotContain("p1").doesNotContain("s1").contains("\"***\"");
	}

	@Test
	void null_은_null() {
		assertThat(DefaultConfigChangeService.toMaskedJson(null)).isNull();
	}

	@Test
	void Class_형_필드가_있어도_직렬화한다() {
		record WithClass(String name, Class<?> type) {
		}

		assertThat(DefaultConfigChangeService.toMaskedJson(new WithClass("job", String.class))).contains("\"name\":\"job\"");
	}
}
