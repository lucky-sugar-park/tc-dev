package com.wow.be.tc.dbconn;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * 외부 DB 연결 설정 ({@code tc.db-connection.*}). 기동 때 읽는다 — 연결 자체는 관리 API 로 재기동 없이 더하고 바꾼다.
 *
 * <pre>
 * tc:
 *   db-connection:
 *     allowed-url-prefixes: [ "jdbc:mariadb:", "jdbc:mysql:" ]
 *     connect-timeout: 5s
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "tc.db-connection")
public class DbConnectionProperties {

	/** 등록할 수 있는 JDBC 주소의 앞부분. tc 에 드라이버가 있는 DB 만 넣는다. */
	private List<String> allowedUrlPrefixes = new ArrayList<>(List.of("jdbc:mariadb:", "jdbc:mysql:"));

	/**
	 * JDBC 주소에 쓸 수 없는 옵션(대소문자 무시). 서버가 tc 의 파일을 읽어 가거나(LOCAL INFILE),
	 * 받은 값을 역직렬화하는 옵션 — 연결 정보를 등록할 수 있는 사람이 tc 서버를 공격하는 길이 된다.
	 */
	private List<String> forbiddenUrlOptions = new ArrayList<>(List.of(
		"allowLocalInfile", "allowLoadLocalInfile", "allowUrlInLocalInfile", "autoDeserialize",
		"allowMultiQueries", "init", "socketFactory"));

	/** 커넥션을 얻을 때까지 기다리는 시간 */
	private Duration connectTimeout = Duration.ofSeconds(5);
}
