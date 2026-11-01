package com.wow.be.tc.schedule;

import java.util.List;
import java.util.Set;

import org.quartz.JobDetail;
import org.quartz.Trigger;

public interface ScheduleManager {
	
	public void startScheduler();
	
	public void standBySchedulet();
	
	public String getSchedulerMetaDataInfo(); 
	
	public String getSchedulerInstanceId();
	public String getSchedulerName();
	
	public JobDetail getJobDetail(String jobName, String jobGroupName);
	public boolean pauseJob(String jobName, String jobGroupName);
	/** 주기와 상관없이 지금 한 번 실행한다(예약된 Job 만) */
	public boolean triggerJob(String jobName, String jobGroupName);
	public boolean resumeJob(String jobName, String jobGroupName);
	public boolean deleteJob(String jobName, String jobGroupName);
	
	public boolean pauseJobGroup(String jobGroupName);	
	public boolean resumeJobGroup(String jobGroupName);
	
	public Trigger getTrigger(String triggerName, String triggerGroupName);
	public boolean pauseTrigger(String triggerName, String triggerGroupName);	
	public boolean resumeTrigger(String triggerName, String triggerGroupName);
	
	public boolean pauseTriggerGroup(String triggerGroupName);	
	public boolean resumeTriggerGroup(String triggerGroupName);
	
	public Set<String> getPausedTriggerGroups();
	
	public boolean pauseAll();	
	public boolean resumeAll();
	
	public boolean scheduleJob(JobDetail jobDetail, Trigger trigger);
	public boolean unscheduleJob(String triggerName, String triggerGroupName);
	
	public String getTriggerState(Trigger trigger);
	public String getTriggerState(String triggerName, String triggerGroupName);
	
	public List<String> getJobGroupNames();
	public List<String> getJobNames(String jobGroupName);
	
	public List<String> getTriggerGroupNames();
	public List<String> getTriggerNames(String triggerGroupName);
	
	public void logTriggers();
	public void logTriggerGroup(String triggerGroupName);
	public void logTrigger(Trigger trigger);
	
	public void logJobs();
	public void logJobGroup(String jobGroupName);
	public void logJobDetail(JobDetail jobDetail);
	
	public String getJobsToPrettyFormat();
	public String getTriggersToPrettyFormat();
}
