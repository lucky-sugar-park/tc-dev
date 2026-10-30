package com.wow.be.tc.dbconn.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.dbconn.DbConnectionProperties;

/**
 * DB 연결 등록 규칙 — JDBC 주소 허용·금지 옵션, 테이블 이름 모양 (docs/db-table-delivery.md §3).
 */
class DbConnectionRulesTest {

	private final DbConnectionProperties props = new DbConnectionProperties();

	@Test
	void 허용_접두어의_주소는_통과() {
		assertThatCode(() -> DefaultDbConnectionService.checkJdbcUrl("jdbc:mariadb://mes-db:3306/mes", props)).doesNotThrowAnyException();
		assertThatCode(() -> DefaultDbConnectionService.checkJdbcUrl("jdbc:mariadb://mes-db/mes?connectTimeout=3000&useSsl=true", props))
			.doesNotThrowAnyException();
	}

	@Test
	void 다른_드라이버는_거부() {
		assertThatThrownBy(() -> DefaultDbConnectionService.checkJdbcUrl("jdbc:h2:mem:x;INIT=RUNSCRIPT FROM 'http://x'", props))
			.isInstanceOf(ToolControlException.class);
		assertThatThrownBy(() -> DefaultDbConnectionService.checkJdbcUrl(null, props)).isInstanceOf(ToolControlException.class);
	}

	@Test
	void 파일_읽기나_역직렬화_옵션은_어디에_있든_거부() {
		for (String url : new String[] {
				"jdbc:mariadb://h/db?allowLocalInfile=true",
				"jdbc:mariadb://h/db?a=1&ALLOWLOCALINFILE = true",
				"jdbc:mysql://h/db?autoDeserialize=true",
				"jdbc:mysql://address=(host=h)(allowLoadLocalInfile=true)/db",
				"jdbc:mariadb://h/db?allowMultiQueries=true" }) {
			assertThatThrownBy(() -> DefaultDbConnectionService.checkJdbcUrl(url, props)).as(url).isInstanceOf(ToolControlException.class);
		}
	}

	@Test
	void 옵션_이름이_값_안에만_있으면_통과() {
		assertThatCode(() -> DefaultDbConnectionService.checkJdbcUrl("jdbc:mariadb://h/allowlocalinfile_db?user=init", props))
			.doesNotThrowAnyException();
	}

	@Test
	void 테이블_이름은_식별자와_스키마_하나만() {
		assertThat(DefaultDbConnectionService.TABLE_PATTERN.matcher("plc_result").matches()).isTrue();
		assertThat(DefaultDbConnectionService.TABLE_PATTERN.matcher("mes.PLC_RESULT").matches()).isTrue();
		assertThat(DefaultDbConnectionService.TABLE_PATTERN.matcher("a.b.c").matches()).isFalse();
		assertThat(DefaultDbConnectionService.TABLE_PATTERN.matcher("t; DROP TABLE x").matches()).isFalse();
		assertThat(DefaultDbConnectionService.TABLE_PATTERN.matcher("`t`").matches()).isFalse();
		assertThat(DefaultDbConnectionService.TABLE_PATTERN.matcher("1t").matches()).isFalse();
	}
}
