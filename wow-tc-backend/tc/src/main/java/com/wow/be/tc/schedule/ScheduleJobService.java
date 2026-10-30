package com.wow.be.tc.schedule;

import java.util.List;

import com.wow.be.framework.Condition.PageInfo;
import com.wow.be.framework.Condition;

public interface ScheduleJobService {
	
	// Schedule job management
	public abstract List<TriggerStatusInfo> getRunningScheduleJobs();
	
	public abstract void loadSchedules();
	public abstract void reloadSchedules();
	
	public abstract int startScheduleJobDatabaseStore(String harewareType);
	
	public abstract int scheduleAll();
	
	public abstract boolean schedule(String scheduleJobName);
	public abstract boolean schedule(ScheduleJobInfo scheduleJob);
	
	public abstract boolean unschedule(String schedulerJobName);
	public abstract boolean unschedule(ScheduleJobInfo schedulerJob);
	public abstract boolean unscheduleByBatch(List<String> scheduleJobNames);
	
	public abstract boolean reschedule(String scheduleJobName);
	public abstract boolean reschedule(ScheduleJobInfo scheduleJob);
	public abstract boolean rescheduleByBatch(List<String> scheduleJobNames);
	
	public boolean pause(String scheduleJobName);
	public boolean pause(ScheduleJobInfo scheduleJob);
	public abstract boolean pauseByBatch(List<String> scheduleJobNames);
	
	public boolean resume(String scheduleJobName);
	public boolean resume(ScheduleJobInfo scheduleJob);
	public abstract boolean resumeByBatch(List<String> scheduleJobNames);
	
	public boolean pauseAll();	
	public boolean resumeAll();
	
	public boolean updateScheduleJobsState();
	
	public abstract void logScheduleHistoryInfo();
	public abstract void logScheduleLogInfo();
	public abstract void logScheduleAwakeInfo();
	public abstract void logScheduleDefaultInfo();
	
	public abstract void logScheduleJobs();

	//=================================================================================
	// Schedule Job Information management
	// schedule job register
	public abstract void registerScheduleJob(ScheduleJobInfo scheduleJob);
	
	// schedule job retrieve
	public abstract List<ScheduleJobInfo> searchScheduleJobs(Condition cond);
	public abstract List<ScheduleJobInfo> searchScheduleJobs(Condition cond, PageInfo pageInfo);
	public abstract List<ScheduleJobInfo> searchScheduleJobs();
	public abstract List<ScheduleJobInfo> searchScheduleJobs(PageInfo pageInfo);
	public abstract ScheduleJobInfo findScheduleJobById(String scheduleJobId);
	public abstract ScheduleJobInfo findScheduleJobByName(String scheduleJobName);
	public abstract ScheduleJobInfo findScheduleJobByName(String scheduleJobName, String schedulerName);
	
	// schedule job update
	public abstract void updateScheduleJob(ScheduleJobInfo scheduleJob);
	public abstract void updateScheduleJobSchedulerName(String schedulerId, String schedulerName);
	public abstract void updateScheduleJobState(String scheduleJobName, String state);
	
	// schedule job delete
	public abstract void deleteScheduleJob(ScheduleJobInfo scheduleJob);
	public abstract void deleteScheduleJobById(String scheduleJobId);
	public abstract void deleteScheduleJobByName(String scheduleJobName);
	public abstract void deleteScheduldJobsByBatch(List<String> scheduleJobIds);
	
	// schedule job detail create
	public abstract void createScheduleJobDetail(ScheduleJobDetailInfo scheduleJobDetail);
	
	// schedule job detail retrieve
	public abstract List<ScheduleJobDetailInfo> searchScheduleJobDetails(ScheduleJobInfo scheduleJob);
	public abstract List<ScheduleJobDetailInfo> searchScheduleJobDetails(String scheduleJobId);
	public abstract List<ScheduleJobDetailInfo> searchScheduleJobDetailsByJobName(String scheduleJobName);
	
	// schedule job detail update
	public abstract void updateScheduleJobDetail(ScheduleJobDetailInfo scheduleJobDetail);
	
	
	// schedule job detail delete
	public abstract void deleteScheduleJobDetails(ScheduleJobInfo scheduleJob);
	public abstract void deleteScheduleJobDetails(String scheduleJobId);
	public abstract void deleteScheduleJobDetail(ScheduleJobDetailInfo scheduleJobDetail);
	public abstract void deleteScheduleJobDetail(String scheduleJobId, String entryKey);

	// history
	public abstract void saveScheduleJobExecHistory(ScheduleJobExecHistoryInfo scheduleJobExecHistory);
	public abstract void deleteScheduleJobExecHistory(long seqNo);
	public abstract void deleteScheduleJobExecHistory(String scheduleJobName);
	public abstract List<ScheduleJobExecHistoryInfo> searchScheduleJobExecHistory() ;
	public abstract List<ScheduleJobExecHistoryInfo> searchScheduleJobExecHistory(PageInfo pageInfo) ;
	public abstract List<ScheduleJobExecHistoryInfo> searchScheduleJobExecHistory(Condition cond);
	public abstract List<ScheduleJobExecHistoryInfo> searchScheduleJobExecHistory(Condition cond, PageInfo pageInfo);
}
