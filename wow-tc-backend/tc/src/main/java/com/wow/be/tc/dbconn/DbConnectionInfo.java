package com.wow.be.tc.dbconn;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wow.be.framework.AbstractInfo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 결과를 기록할 외부 DB 연결 하나 (DB_CONNECTION). DB_TABLE 전달 정의가 이름으로 참조한다.
 *
 * <p>비밀번호는 받기만 하고 돌려주지 않는다(응답에는 {@link #passwordSet} 만). 저장은 {@code TC_SECRET_KEY} 로 암호화한다.
 */
@Data
@ToString(callSuper = true, exclude = "password")
@EqualsAndHashCode(callSuper = false)
public class DbConnectionInfo extends AbstractInfo {

	/** 연결 이름. 영문 소문자·숫자·밑줄·하이픈(예: {@code mes-db}). {@code tc} 는 tc 자체 DB 라 쓸 수 없다. */
	private String name;

	/** JDBC 주소(예: {@code jdbc:mariadb://mes-db:3306/mes}). 허용 접두어와 금지 옵션은 tc.db-connection 설정 */
	private String jdbcUrl;

	private String username;

	/** 등록할 때 넣는다. 수정할 때 비우면 기존 비밀번호를 그대로 쓴다. */
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private String password;

	/** 저장된 비밀번호가 있는가(응답 전용) */
	private boolean passwordSet;

	/** 이 연결의 최대 커넥션 수 */
	private int maxPoolSize = 2;

	private boolean use = true;

	private String description;

	@Override
	public String toPlainText() {
		return name + " [" + jdbcUrl + "]";
	}
}
