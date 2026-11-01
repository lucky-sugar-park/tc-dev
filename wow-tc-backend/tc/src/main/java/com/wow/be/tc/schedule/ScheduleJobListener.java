package com.wow.be.tc.schedule;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/** 모든 Job 의 실행 전후를 디버그 로그로 남긴다. 실행 이력은 {@link ScheduleJob} 이 남긴다. */
@Component
@Slf4j
public class ScheduleJobListener implements JobListener {

	@Override
	public String getName() {
		return "globalJob";
	}

	@Override
	public void jobToBeExecuted(JobExecutionContext context) {
		log.debug("jobToBeExecuted: {}", context.getJobDetail().getKey());
	}

	@Override
	public void jobExecutionVetoed(JobExecutionContext context) {
		log.info("jobExecutionVetoed: {}", context.getJobDetail().getKey());
	}

	@Override
	public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
		log.debug("jobWasExecuted: {}", context.getJobDetail().getKey());
	}
}
