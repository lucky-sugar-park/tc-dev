package com.wow.be.tc.schedule;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.JobListener;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ScheduleJobListener implements JobListener {
	
	@Value("${scheduler.name.default}")
	private String defaultSchedulerName;
	
//	@Autowired
//	private ScheduleJobService scheduleJobService;

	@Override
    public String getName() {
        return "globalJob";
    }

    @Override
    public void jobToBeExecuted(JobExecutionContext context) {
        JobKey jobKey = context.getJobDetail().getKey();

//        try {
//			scheduleJobService.updateScheduleJobSchedulerName(jobKey.getName(), context.getScheduler().getSchedulerName());
//		} catch (SchedulerException e) {
//			log.warn("", e);
//		}
        log.info("jobToBeExecuted :: jobkey : {}", jobKey);
    }

    @Override
    public void jobExecutionVetoed(JobExecutionContext context) {
        JobKey jobKey = context.getJobDetail().getKey();
        log.info("jobExecutionVetoed :: jobkey : {}", jobKey);
    }

    @Override
    @Transactional
    public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
        JobKey jobKey = context.getJobDetail().getKey();
        log.info("jobWasExecuted :: jobkey : {}", jobKey);
//      scheduleJobService.updateScheduleJobSchedulerName(jobKey.getName(), "RELEASED");
        
        ScheduleJobExecHistoryInfo history=new ScheduleJobExecHistoryInfo();
		history.setJobName(context.getJobDetail().getKey().getName());
		try {
			history.setSchedulerName(context.getScheduler().getSchedulerName());
		} catch (SchedulerException e) {
			log.warn("", e);
			history.setSchedulerName(defaultSchedulerName);
		}
		history.setExecutionResult(context.getResult()!=null ? "SUCCESS" : "FAIL");
		history.setExecutionTime(context.getFireTime());
		history.setNextFireTime(context.getNextFireTime());

//		scheduleJobService.saveScheduleJobExecHistory(history);
    }
}
