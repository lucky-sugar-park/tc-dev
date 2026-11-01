package com.wow.be.tc.schedule;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;

import com.wow.be.framework.Condition.PageInfo;
import com.wow.be.framework.Condition;

public interface ScheduleJobExecHistoryService {

	public abstract void saveScheduleJobExecHistory(ScheduleJobExecHistoryInfo scheduleJobExecHistoryInfo);
	public abstract void deleteScheduleJobExecHistory(long seqNo);
	public abstract void deleteScheduleJobExecHistory(String scheduleJobName);
	public abstract void deleteScheduleJobExecHistoryBetweenExecTime(Date from, Date to);
	public abstract List<ScheduleJobExecHistoryInfo> searchScheduleJobExecHistory() ;
	public abstract List<ScheduleJobExecHistoryInfo> searchScheduleJobExecHistory(PageInfo pageInfo) ;
	public abstract List<ScheduleJobExecHistoryInfo> searchScheduleJobExecHistory(Condition cond);
	public abstract List<ScheduleJobExecHistoryInfo> searchScheduleJobExecHistory(Condition cond, PageInfo pageInfo);

	/** 실행 이력 — 최신순 한 쪽 */
	public abstract Page<ScheduleJobExecHistoryInfo> query(ScheduleJobExecHistoryQuery q);

	/** Job 이름 → 가장 최근 실행 이력 */
	public abstract Map<String, ScheduleJobExecHistoryInfo> latestByJob();

	/** Job 이름 → since 이후 실패 수(실패가 없는 Job 은 빠진다) */
	public abstract Map<String, Long> failCountsSince(Date since);
}
