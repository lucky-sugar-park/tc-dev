package com.wow.be.tc.schedule;

/**
 * 정리 Job({@code DataTableFlashJob})이 오래된 행을 지울 수 있는 표. 표·시각 칼럼은 여기 고정이다 — 속성으로 표 이름을 받지 않는다.
 * 설정 변경 이력(CONFIG_CHANGE_HIST)은 자체 보존 정리({@code tc.config-history})가 있어 넣지 않는다.
 */
public enum CleanupTarget {

	/** 송수신 기록 */
	EXCHANGED_MESSAGE_AUDIT("EXCHANGED_MESSAGE_AUDIT", "TIMESTAMP"),

	/** PLC 연결 이벤트 이력 */
	PLC_CONNECTION_HIST("PLC_CONNECTION_HIST", "EVENT_TIMESTAMP"),

	/** 스케줄 Job 실행 이력 */
	SCHEDULE_JOB_EXEC_HIST("SCHEDULE_JOB_EXEC_HIST", "EXEC_TIME");

	private final String table;

	private final String timeColumn;

	CleanupTarget(String table, String timeColumn) {
		this.table = table;
		this.timeColumn = timeColumn;
	}

	public String table() {
		return table;
	}

	public String timeColumn() {
		return timeColumn;
	}
}
