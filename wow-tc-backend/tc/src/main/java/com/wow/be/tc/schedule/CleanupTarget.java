package com.wow.be.tc.schedule;

/**
 * 정리 Job({@code DataTableFlashJob})이 오래된 행을 지울 수 있는 표. 표·키·시각 칼럼은 여기 고정이다 — 속성으로 표 이름을 받지 않는다.
 * 키는 자동 증가 기본 키다 — 정리는 기본 키 범위로 지운다({@code DataCleaner}).
 * 설정 변경 이력(CONFIG_CHANGE_HIST)은 자체 보존 정리({@code tc.config-history})가 있어 넣지 않는다.
 */
public enum CleanupTarget {

	/** 송수신 기록 */
	EXCHANGED_MESSAGE_AUDIT("EXCHANGED_MESSAGE_AUDIT", "ID", "TIMESTAMP"),

	/** PLC 연결 이벤트 이력 */
	PLC_CONNECTION_HIST("PLC_CONNECTION_HIST", "ID", "EVENT_TIMESTAMP"),

	/** 스케줄 Job 실행 이력 */
	SCHEDULE_JOB_EXEC_HIST("SCHEDULE_JOB_EXEC_HIST", "SEQ_NO", "EXEC_TIME");

	private final String table;

	private final String idColumn;

	private final String timeColumn;

	CleanupTarget(String table, String idColumn, String timeColumn) {
		this.table = table;
		this.idColumn = idColumn;
		this.timeColumn = timeColumn;
	}

	public String table() {
		return table;
	}

	public String idColumn() {
		return idColumn;
	}

	public String timeColumn() {
		return timeColumn;
	}
}
