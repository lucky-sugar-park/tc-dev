package com.wow.be.tc.schedule;

import java.util.Date;

/**
 * 실행 이력 검색 조건. 모두 선택이다.
 *
 * @param jobName   Job 이름(같음) — Job 화면의 실행 이력 탭
 * @param jobSearch Job 이름·설명에 든 글(대소문자 무시). 지운 Job 의 이력도 이름으로 찾는다
 * @param jobState  Job 상태(NORMAL·PAUSED·ERROR·BLOCKED·COMPLETE·NONE) — 지금 있는 Job 의 이력만
 * @param from      실행 시각 이상
 * @param to        실행 시각 미만
 * @param result    SUCCESS·FAIL
 * @param page      0부터
 * @param size      1~500, 기본 50
 */
public record ScheduleJobExecHistoryQuery(String jobName, String jobSearch, String jobState, Date from, Date to, String result,
		Integer page, Integer size) {

	public static ScheduleJobExecHistoryQuery ofJob(String jobName, int page, int size) {
		return new ScheduleJobExecHistoryQuery(jobName, null, null, null, null, null, page, size);
	}
}
