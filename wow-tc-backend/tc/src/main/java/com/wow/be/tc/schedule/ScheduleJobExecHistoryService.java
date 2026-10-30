package com.wow.be.tc.schedule;

import java.util.Date;
import java.util.List;

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
}
