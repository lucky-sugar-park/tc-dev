package com.wow.be.tc.schedule;

import java.util.Date;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.SchedulerException;
import org.springframework.context.ApplicationContext;

import com.wow.be.framework.session.SessionContext;
import com.wow.be.tc.ToolControlException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class ScheduleJob implements Job {
	
	protected ApplicationContext applicationContext;
	
	protected ScheduleJobService scheduleJobService;
	
	public ScheduleJob () {
		applicationContext=SessionContext.getGlobalSessionContext().getApplicationContext();
		scheduleJobService=(ScheduleJobService)applicationContext.getBean(ScheduleJobService.class);
	}
	
	/**
	 * 일반적인 흐름
	 * 1. PLC에 접속한다 (커넥션은 이미 생성되어 있어야 함)
	 * 2. WRITE or READ
	 * 3. PLC로부터의 응답을 webHook으로 전송한다 
	 */
	@Override
	public void execute(JobExecutionContext context) throws JobExecutionException {
		log.debug("ScheduleJob.execute(JobExecutionContext context) ...");

		JobKey jobKey=null;
		String schedulerName=null;

		long startTime=System.currentTimeMillis();
		String failure=null;
		String jobDetailName=(context.getJobDetail()!=null) ? context.getJobDetail().getKey().getName() : "";
		try {
			jobKey = context.getJobDetail().getKey();
			schedulerName=context.getScheduler().getSchedulerName();
			this.updateScheduleJob(jobKey.getName(), schedulerName);

			this.executeJob(context);
		} catch (ToolControlException e) {
			long endTime = System.currentTimeMillis();
			log.error("failed: {}, elasped: {}, context: {}", jobDetailName, (endTime-startTime), context, e);
			failure=e.getErrorCode() + ": " + e.getMessage();
		} catch (SchedulerException e) {
			log.warn("");
		} finally {
			long endTime = System.currentTimeMillis();
			log.info("completed: {}, elasped: {}, context: {}", jobDetailName, (endTime-startTime), context);

			this.updateScheduleJob (jobKey.getName(), "NO_ASSIGNED");
			this.saveJobExecHistory (
					jobKey.getName(), 
					schedulerName!=null?schedulerName:(String)context.getJobDetail().getJobDataMap().get("defaultSchedulerName"),
					context.getResult()!=null ? "SUCCESS" : "FAIL",
					context.getFireTime(),
					context.getNextFireTime(),
					// 성공이면 결과, 실패면 오류 코드와 사유 — 실패 사유가 이력에 없어 원인을 로그에서만 찾아야 했다
					failure != null ? failure
						: context.getResult() instanceof String ? (String) context.getResult()
						: context.getResult() != null ? context.getResult().toString() : ""
			);
		}
	}

	protected void updateScheduleJob(String jobName, String schedulerName) {
		log.debug("");
		scheduleJobService.updateScheduleJobSchedulerName(jobName, schedulerName);
	}

	protected void saveJobExecHistory(String jobName, String shedulerName, String result, Date execTime, Date nextFireTime, String remark) {
		log.debug("");

		ScheduleJobExecHistoryInfo history=new ScheduleJobExecHistoryInfo();
		history.setJobName(jobName);
		history.setSchedulerName(shedulerName);
		history.setExecutionResult(result);
		history.setExecutionTime(execTime);
		history.setNextFireTime(nextFireTime);
		history.setRemark(remark);

		scheduleJobService.saveScheduleJobExecHistory(history);
	}

	public abstract void executeJob(JobExecutionContext context) throws ToolControlException;
}
