package com.wow.be.tc.dbconn;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import com.wow.be.tc.ToolControlException;

/**
 * 외부 DB 연결(DB_CONNECTION) 관리와 커넥션 풀. 등록·수정·삭제는 재기동 없이 바로 반영된다.
 *
 * <p>이름 {@value #TC} 는 tc 자체 DB 다 — 등록하지 않아도 쓸 수 있고 바꾸거나 지울 수 없다.
 */
public interface DbConnectionService {

	/** tc 자체 DB 의 예약 이름 */
	String TC = "tc";

	DbConnectionInfo register(DbConnectionInfo info) throws ToolControlException;

	/** 비밀번호를 비우면 기존 값을 쓴다 */
	DbConnectionInfo update(DbConnectionInfo info) throws ToolControlException;

	/** DB_TABLE 전달 정의가 쓰고 있으면 ERR_409 */
	void delete(String name) throws ToolControlException;

	DbConnectionInfo find(String name) throws ToolControlException;

	List<DbConnectionInfo> searchAll();

	/** 커넥션을 하나 얻어 확인한다. 결과: {@code success}, {@code message}(실패 사유), {@code database}(DB 제품·버전) */
	Map<String, Object> test(String name);

	/**
	 * 결과 기록에 쓸 DataSource. 연결 정보가 바뀌었으면 풀을 새로 만든다.
	 *
	 * @throws ToolControlException 없거나(ERR_404) 사용 중지(ERR_409)일 때
	 */
	DataSource dataSource(String name) throws ToolControlException;

	/**
	 * 테이블이 있고 필요한 칼럼을 모두 가졌는지 JDBC 메타데이터로 확인한다(대소문자 무시).
	 *
	 * @throws ToolControlException 테이블·칼럼이 없거나(ERR_400) DB 에 연결하지 못했을 때(ERR_400)
	 */
	void validateTable(String name, String table, Collection<String> requiredColumns) throws ToolControlException;
}
