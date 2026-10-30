package com.wow.be.tc.delivery.impl;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wow.be.tc.ToolControlException;
import com.wow.be.tc.dbconn.DbConnectionService;
import com.wow.be.tc.dbconn.impl.DefaultDbConnectionService;
import com.wow.be.tc.delivery.DeliveryTarget;
import com.wow.be.tc.delivery.ResultDeliveryChannel;
import com.wow.be.tc.dispatch.TcMessageRequest;
import com.wow.be.tc.dispatch.TcMessageResponse;
import com.wow.be.tc.interfs.InterfaceInfo;
import com.wow.be.tc.interfs.InterfaceService;

/**
 * 결과 한 건을 등록한 DB 연결의 테이블에 한 행으로 넣는다(docs/db-table-delivery.md).
 *
 * <p>공통 정보는 칼럼에, 데이터 값은 {@code PAYLOAD} 에 JSON 문자열로 넣는다 — 인터페이스의 데이터 속성이 바뀌어도
 * 테이블을 고칠 필요가 없다. 실패 응답도 넣는다(전달 정의의 deliverOn 으로 거를 수 있다).
 *
 * <p>테이블 이름은 등록할 때 모양({@link DefaultDbConnectionService#TABLE_PATTERN})과 실제 칼럼을 확인한 값만 SQL 에 들어가고,
 * 값은 모두 바인드 변수다.
 */
@Component
public class DbTableDeliveryChannel implements ResultDeliveryChannel {

	/** 대상 테이블이 가져야 하는 칼럼. 자동 증가 키 등 다른 칼럼은 있어도 된다 */
	public static final List<String> COLUMNS = List.of(
		"TRANSACTION_ID", "INTERFACE_NAME", "PLC_NAME", "SUCCESS", "ERROR_CODE", "ERROR_MESSAGE", "COLLECTED_AT", "PAYLOAD");

	private static final int MAX_ERROR_MESSAGE = 1000;

	private static final ObjectMapper JSON = new ObjectMapper();

	private final DbConnectionService dbConnectionService;

	private final InterfaceService interfaceService;

	public DbTableDeliveryChannel(DbConnectionService dbConnectionService, InterfaceService interfaceService) {
		this.dbConnectionService = dbConnectionService;
		this.interfaceService = interfaceService;
	}

	@Override
	public DeliveryTarget.DeliveryType type() {
		return DeliveryTarget.DeliveryType.DB_TABLE;
	}

	@Override
	public void deliver(DeliveryTarget target, TcMessageRequest request, TcMessageResponse response) {
		// 등록 뒤 정의를 DB 에서 직접 고친 경우까지 막는다 — 모양이 다르면 SQL 을 만들지 않는다
		if (target.table() == null || !DefaultDbConnectionService.TABLE_PATTERN.matcher(target.table()).matches()) {
			throw new IllegalStateException("테이블 이름이 올바르지 않습니다: " + target.table());
		}
		NamedParameterJdbcTemplate jdbc;
		try {
			jdbc = new NamedParameterJdbcTemplate(dbConnectionService.dataSource(target.name()));
		} catch (ToolControlException e) {
			throw new IllegalStateException(e.getMessage(), e);
		}
		jdbc.update(insertSql(target.table()), params(request, response));
	}

	static String insertSql(String table) {
		return "INSERT INTO " + table + " (" + String.join(", ", COLUMNS) + ") VALUES ("
			+ String.join(", ", COLUMNS.stream().map(c -> ":" + c).toList()) + ")";
	}

	private MapSqlParameterSource params(TcMessageRequest request, TcMessageResponse response) {
		String opcode = response.getOpcode() != null ? response.getOpcode() : request.getOpcode();
		InterfaceInfo iInfo = interfaceService.findInterfaceByName(opcode);
		String error = response.getErrorMessage();
		if (error != null && error.length() > MAX_ERROR_MESSAGE) {
			error = error.substring(0, MAX_ERROR_MESSAGE);
		}
		long collectedAt = response.getTimestamp() != null ? response.getTimestamp() : System.currentTimeMillis();
		return new MapSqlParameterSource()
			.addValue("TRANSACTION_ID", response.getTransactionId() != null ? response.getTransactionId() : request.getTransactionId())
			.addValue("INTERFACE_NAME", opcode)
			.addValue("PLC_NAME", iInfo != null ? iInfo.getPlcName() : null)
			.addValue("SUCCESS", !Boolean.FALSE.equals(response.getSuccess()))
			.addValue("ERROR_CODE", response.getErrorCode())
			.addValue("ERROR_MESSAGE", error)
			.addValue("COLLECTED_AT", new Timestamp(collectedAt))
			.addValue("PAYLOAD", toJson(response.getData()));
	}

	private static String toJson(Map<String, Object> data) {
		try {
			return JSON.writeValueAsString(data != null ? data : Map.of());
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("결과 데이터를 JSON 으로 바꾸지 못했습니다: " + e.getOriginalMessage(), e);
		}
	}
}
