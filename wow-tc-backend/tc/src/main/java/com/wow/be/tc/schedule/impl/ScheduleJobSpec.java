package com.wow.be.tc.schedule.impl;

import java.util.Date;

import org.springframework.data.jpa.domain.Specification;

import com.wow.be.framework.Condition.CondType;
import com.wow.be.framework.persist.SearchSpec;

public class ScheduleJobSpec extends SearchSpec<ScheduleJobEntity> {

	public Specification<ScheduleJobEntity> withId(CondType condType, String id) {
		return super.with("id", condType, id);
	}
	
	public Specification<ScheduleJobEntity> withName(CondType condType, String name) {
		return super.with("name", condType, name);
	}
	
	public Specification<ScheduleJobEntity> withSchedulerName(CondType condType, String schedulerName) {
		return super.with("schedulerName", condType, schedulerName);
	}
	
	public Specification<ScheduleJobEntity> withUse(CondType condType, Boolean use) {
		return super.with("use", condType, use);
	}
	
	public Specification<ScheduleJobEntity> withGroupName(CondType condType, String groupName) {
		return super.with("groupName", condType, groupName);
	}

	public Specification<ScheduleJobEntity> withTriggerState(CondType condType, String triggerState) {
		return super.with("triggerState", condType, triggerState);
	}
	
	public Specification<ScheduleJobEntity> withStateChangedTime(CondType condType, Date stateChangedTime) {
		return super.with("stateChangedTime", condType, stateChangedTime);
	}
	
	public Specification<ScheduleJobEntity> withTriggerType(CondType condType, String triggerType) {
		return super.with("triggerType", condType, triggerType);
	}
	
	public Specification<ScheduleJobEntity> withStartDelay(CondType condType, long startDelay) {
		return super.with("startDelay", condType, startDelay);
	}
	
	public Specification<ScheduleJobEntity> withRepeatInterval(CondType condType, long repeatInterval) {
		return super.with("repeatInterval", condType, repeatInterval);
	}
	
	public Specification<ScheduleJobEntity> withCronExpression(CondType condType, String cronExpression) {
		return super.with("cronExpression", condType, cronExpression);
	}
	
	public Specification<ScheduleJobEntity> withDescription(CondType condType, String description) {
		return super.with("description", condType, description);
	}
}

