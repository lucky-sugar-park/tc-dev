package com.wow.be.tc.schedule;

import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.Trigger.CompletedExecutionInstruction;
import org.quartz.Trigger;
import org.quartz.TriggerListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ScheduleTriggerListener implements TriggerListener {

	@Override
	public String getName() {
		return "globalTrigger";
	}

	@Override
	public void triggerFired(Trigger trigger, JobExecutionContext context) {
		JobKey jobKey = trigger.getJobKey();
        log.info("triggerFired at {} :: jobkey : {}", trigger.getStartTime(), jobKey);
	}

	@Override
	public boolean vetoJobExecution(Trigger trigger, JobExecutionContext context) {
		return false;
	}

	@Override
	public void triggerMisfired(Trigger trigger) {
		JobKey jobKey = trigger.getJobKey();
        log.info("triggerMisfired at {} :: jobkey : {}", trigger.getStartTime(), jobKey);
	}

	@Override
	public void triggerComplete(Trigger trigger, JobExecutionContext context, CompletedExecutionInstruction triggerInstructionCode) {
		JobKey jobKey = trigger.getJobKey();
        log.info("triggerComplete at {} :: jobkey : {}", trigger.getStartTime(), jobKey);
	}
}
