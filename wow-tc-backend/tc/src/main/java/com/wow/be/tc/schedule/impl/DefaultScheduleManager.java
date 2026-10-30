package com.wow.be.tc.schedule.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.quartz.CronTrigger;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.wow.be.tc.schedule.ScheduleConstants;
import com.wow.be.tc.schedule.ScheduleManager;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DefaultScheduleManager implements ScheduleManager {
	
	public static final String TRIGGER_STATE_NORMAL = "NORMAL";
	public static final String TRIGGER_STATE_PAUSED = "PAUSED";
	public static final String TRIGGER_STATE_COMPLETE = "COMPLETE";
    public static final String TRIGGER_STATE_ERROR = "ERROR";
    public static final String TRIGGER_STATE_BLOCKED = "BLOCKED";
    public static final String TRIGGER_STATE_NONE = "NONE";
	
    @Autowired
	protected Scheduler scheduler;
    
    @PreDestroy
    public void release() {
    	log.info("Shutdowning quartz schedule.");
    	try {
			scheduler.shutdown();
		} catch (SchedulerException e) {
			e.printStackTrace();
		}
    	log.info("Quartz schedule's shutdown is completed.");
    }
	
	@PostConstruct
	public void initAndCheck() {
		log.info("Initialize and check shcedule jobs.");
		
		try {
			// 1.5초 후에 스케쥴러를 시작함
			log.info("scheduler: {}", scheduler.getSchedulerName() );
			scheduler.startDelayed(1500);
		} catch (SchedulerException e) {
			log.warn("", e);
		}
	}
	
	@Override
	public void startScheduler() {
		log.info("");
		try {
			scheduler.start();
		} catch (SchedulerException e) {
			log.warn("", e);
		}
	}

	@Override
	public void standBySchedulet() {
		log.info("");
		try {
			scheduler.standby();
		} catch (SchedulerException e) {
			log.warn("", e);
		}
	}

	@Override
	public String getSchedulerMetaDataInfo() {
		String schedulerMetaDataInfo = "";
		try {
			schedulerMetaDataInfo = scheduler.getMetaData().toString();
			log.info(schedulerMetaDataInfo);
		} catch (SchedulerException e) {
			log.error("", e);
		}
		return schedulerMetaDataInfo;
	}

	@Override
	public String getSchedulerInstanceId() {
		String schedulerInstanceId = "";
		try {
			schedulerInstanceId = scheduler.getSchedulerInstanceId();
			log.info(schedulerInstanceId);
		} catch (SchedulerException e) {
			log.error("", e);
		}
		return schedulerInstanceId;
	}

	@Override
	public String getSchedulerName() {
		String schedulerName = "";
		try {
			schedulerName = scheduler.getSchedulerName();
			log.info(schedulerName);
		} catch (SchedulerException e) {
			log.error("", e);
		}
		return schedulerName;
	}

	@Override
	public JobDetail getJobDetail(String jobName, String jobGroupName) {
		JobDetail jobDetail = null;
		try {
			jobDetail = scheduler.getJobDetail(new JobKey(jobName, jobGroupName));
		} catch (SchedulerException e) {
			log.error("", e);
		}
		return jobDetail;
	}

	@Override
	public boolean pauseJob(String jobName, String jobGroupName) {
		boolean result = false;
		try {
			scheduler.pauseJob(new JobKey(jobName, jobGroupName));
			log.info(jobName + " is paused");
			result = true;
		} catch (SchedulerException e) {
			log.error("", e);
		}
		return result;
	}

	@Override
	public boolean resumeJob(String jobName, String jobGroupName) {
		boolean result = false;
		try {
			scheduler.resumeJob(new JobKey(jobName, jobGroupName));
			log.info(jobName + " is resumed");
			result = true;
		} catch (SchedulerException e) {
			log.error("", e);
		}
		return result;
	}

	@Override
	public boolean deleteJob(String jobName, String jobGroupName) {
		boolean result = false;
		try {
			scheduler.deleteJob(new JobKey(jobName, jobGroupName));
			log.info(jobName + " is deleted");
			result = true;
		} catch (SchedulerException e) {
			log.error("", e);
		}
		return result;
	}

	@Override
	public boolean pauseJobGroup(String jobGroupName) {
		boolean result = false;
		try {
			GroupMatcher<JobKey> matcher = GroupMatcher.jobGroupEquals(jobGroupName);
			scheduler.pauseJobs(matcher);
			log.info(jobGroupName + " were paused");
			result = true;
		} catch (SchedulerException e) {
			log.error("", e);
		}
		return result;
	}

	@Override
	public boolean resumeJobGroup(String jobGroupName) {
		boolean result = false;
		try {
			GroupMatcher<JobKey> matcher = GroupMatcher.jobGroupEquals(jobGroupName);
			scheduler.resumeJobs(matcher);
			log.info(jobGroupName + " were resumed");
			result = true;
		} catch (SchedulerException e) {
			log.error("", e);
		}
		return result;
	}

	@Override
	public Trigger getTrigger(String triggerName, String triggerGroupName) {
		Trigger trigger = null;
		try {
			trigger = scheduler.getTrigger(new TriggerKey(triggerName, triggerGroupName));
			if(trigger!=null) {
				log.debug("trigger name: {}", trigger.getCalendarName());
			}
		} catch (SchedulerException e) {
			log.error("getTrigger failed.", e);
		}
		return trigger;
	}

	@Override
	public boolean pauseTrigger(String triggerName, String triggerGroupName) {
		boolean result = false;
		try {
			scheduler.pauseTrigger(new TriggerKey(triggerName, triggerGroupName));
			log.info(triggerName + " is paused");
			result = true;
		} catch (SchedulerException e) {
			log.error("", e);
		}
		return result;
	}

	@Override
	public boolean resumeTrigger(String triggerName, String triggerGroupName) {
		boolean result = false;
		try {
			scheduler.resumeTrigger(new TriggerKey(triggerName, triggerGroupName));
			log.info(triggerName + " is resumed");
			result = true;
		} catch (SchedulerException e) {
			log.error("resumeTrigger failed. triggerName:{}, triggerGroupName:{}", triggerName, triggerGroupName, e);
		}
		return result;
	}

	@Override
	public boolean pauseTriggerGroup(String triggerGroupName) {
		boolean result = false;
		try {
			GroupMatcher<TriggerKey> matcher = GroupMatcher.triggerGroupEquals(triggerGroupName);
			scheduler.pauseTriggers(matcher);
			log.info(triggerGroupName + " were paused");
			result = true;
		} catch (SchedulerException e) {
			log.error("pauseTriggerGroup failed. triggerGroupName: {}", triggerGroupName, e);
		}
		return result;
	}

	@Override
	public boolean resumeTriggerGroup(String triggerGroupName) {
		boolean result = false;
		try {
			GroupMatcher<TriggerKey> matcher = GroupMatcher.triggerGroupEquals(triggerGroupName);
			scheduler.resumeTriggers(matcher);
			log.info(triggerGroupName + " were resumed");
			result = true;
		} catch (SchedulerException e) {
			log.error("resumeTriggerGroup failed. triggerGroupName: {}", triggerGroupName, e);
		}
		return result;
	}

	@Override
	public Set<String> getPausedTriggerGroups() {
		Set<String> pausedTriggerGroups = null;
		try {
			pausedTriggerGroups = scheduler.getPausedTriggerGroups();
			log.info("puasedTriggerGroup: {}", pausedTriggerGroups);
		} catch (SchedulerException e) {
			log.error("getPausedTriggerGroups failed.", e);
		}
		return pausedTriggerGroups;
	}

	@Override
	public boolean pauseAll() {
		boolean result = false;
		try {
			scheduler.pauseAll();
			log.info("all paused");
			result = true;
		} catch (SchedulerException e) {
			log.error("", e);
		}
		return result;
	}

	@Override
	public boolean resumeAll() {
		boolean result = false;
		try {
			scheduler.resumeAll();
			log.info("all resumed");
			result = true;
		} catch (SchedulerException e) {
			log.error("", e);
		}
		return result;
	}

	@Override
	public boolean scheduleJob(JobDetail jobDetail, Trigger trigger) {
		log.debug("");
		boolean result = false;
		
		try {
			scheduler.scheduleJob(jobDetail, trigger);
			log.info(jobDetail.getKey().getName() + " is scheduled, " + jobDetail + ", " + trigger);
			result = true;
		} catch (SchedulerException e) {
			e.printStackTrace();
			log.error("", e);
		}
		
		return result;
	}

	@Override
	public boolean unscheduleJob(String triggerName, String triggerGroupName) {
		boolean result = false;
		
		try {
			result = scheduler.unscheduleJob(new TriggerKey(triggerName, triggerGroupName));
			if(result) {
				log.info(triggerName + " is unscheduled");
			}
		} catch (SchedulerException e) {
			e.printStackTrace();
			log.error("", e);
		}
		
		return result;
	}

	@Override
	public String getTriggerState(Trigger trigger) {
		return this.getTriggerState(trigger.getKey().getName(), trigger.getJobKey().getGroup());
	}

	@Override
	public String getTriggerState(String triggerName, String triggerGroupName) {
		String triggerState=ScheduleConstants.TRIGGER_STATE_NONE;		
		try {
			triggerState = this.scheduler.getTriggerState(new TriggerKey(triggerName, triggerGroupName)).name();
		} catch (SchedulerException e) {
			log.error("", e);
		}
		
		return triggerState;
	}

	@Override
	public List<String> getJobGroupNames() {
		try {
			return this.scheduler.getJobGroupNames();
		} catch (SchedulerException e) {
			log.error("", e);
		}
		
		return new ArrayList<String>();
	}

	@Override
	public List<String> getJobNames(String jobGroupName) {
		List<String> jobNames = new ArrayList<>();
		try {
			GroupMatcher<JobKey> matcher = GroupMatcher.jobGroupEquals(jobGroupName);
			
			this.scheduler.getJobKeys(matcher).forEach((jobKey)->{
				jobNames.add(jobKey.getName());
			});
		} catch (SchedulerException e) {
			log.error("", e);
		}
		
		return jobNames;
	}

	@Override
	public List<String> getTriggerGroupNames() {
		try {
			return scheduler.getTriggerGroupNames();
		} catch (SchedulerException e) {
			log.error("", e);
		}
		
		return new ArrayList<>();
	}

	@Override
	public List<String> getTriggerNames(String triggerGroupName) {
		List<String> triggerNames = new ArrayList<>();
		try {
			GroupMatcher<TriggerKey> matcher = GroupMatcher.triggerGroupEquals(triggerGroupName);
			
			this.scheduler.getTriggerKeys(matcher).forEach((key)->{
				triggerNames.add(key.getName());
			});
		} catch (SchedulerException e) {
			log.error("", e);
		}
		
		return triggerNames;
	}

	@Override
	public void logTriggers() {
		try {
			scheduler.getTriggerGroupNames().forEach((name)->{
				logTriggerGroup(name);
			});
		} catch (SchedulerException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void logTriggerGroup(String triggerGroupName) {
		getTriggerNames(triggerGroupName).forEach((triggerName)->{
			Trigger trigger = this.getTrigger(triggerName, triggerGroupName);
			if(trigger != null) logTrigger(trigger);
		});
	}

	@Override
	public void logTrigger(Trigger trigger) {
		StringBuffer sb = new StringBuffer();
		
		sb.append("trigger{" + trigger.getKey().getName() + "}, ");
		sb.append("state{" + this.getTriggerState(trigger.getKey().getName(), trigger.getKey().getGroup()) + "}, ");
		
		if(trigger instanceof CronTrigger) {
			sb.append("expression{" + ((CronTrigger)trigger).getCronExpression() + "}, ");
			sb.append("start{" + ((CronTrigger)trigger).getStartTime() + "}, ");
			sb.append("end{" + ((CronTrigger)trigger).getEndTime() + "}, ");
			sb.append("final{" + ((CronTrigger)trigger).getFinalFireTime() + "}, ");
			sb.append("previous{" + ((CronTrigger)trigger).getPreviousFireTime() + "}, ");
			sb.append("next{" + ((CronTrigger)trigger).getNextFireTime() + "}");
			
		}else if(trigger instanceof SimpleTrigger) {
			sb.append("interval{" + ((SimpleTrigger)trigger).getRepeatInterval() + "}, ");
			sb.append("repeat{" + ((SimpleTrigger)trigger).getRepeatCount() + "}, ");
			sb.append("triggered{" + ((SimpleTrigger)trigger).getTimesTriggered() + "}, ");
			sb.append("start{" + ((SimpleTrigger)trigger).getStartTime() + "}, ");
			sb.append("end{" + ((SimpleTrigger)trigger).getEndTime() + "}, ");
			sb.append("final{" + ((SimpleTrigger)trigger).getFinalFireTime() + "}, ");
			sb.append("previous{" + ((SimpleTrigger)trigger).getPreviousFireTime() + "}, ");
			sb.append("next{" + ((SimpleTrigger)trigger).getNextFireTime() + "}");
		}
		
		log.info(sb.toString());
	}

	@Override
	public void logJobs() {
		try {
			scheduler.getJobGroupNames().forEach((name)->{
				logJobGroup(name);
			});
		} catch (SchedulerException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void logJobGroup(String jobGroupName) {
		getJobNames(jobGroupName).forEach((jobName)->{
			JobDetail jobDetail = this.getJobDetail(jobName, jobGroupName);
			if(jobDetail != null) logJobDetail(jobDetail);
		});
	}

	@Override
	public void logJobDetail(JobDetail jobDetail) {
		StringBuffer sb = new StringBuffer();
		
		sb.append("job{" + jobDetail.getKey().getName() + "}, ");
		JobDataMap jobDataMap = jobDetail.getJobDataMap();
		
		jobDataMap.entrySet().forEach((entry)->{
			sb.append(entry.getKey() + "{" + entry.getValue() + "}, ");
		});
		
		sb.append("class{" + jobDetail.getJobClass() + "}");
		log.info(sb.toString());
	}

	@Override
	public String getJobsToPrettyFormat() {
		return new StringBuffer().toString();
	}

	@Override
	public String getTriggersToPrettyFormat() {
		return new StringBuffer().toString();
	}
}
