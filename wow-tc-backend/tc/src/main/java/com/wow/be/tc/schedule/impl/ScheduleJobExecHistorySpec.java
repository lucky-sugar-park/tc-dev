package com.wow.be.tc.schedule.impl;

import org.springframework.data.jpa.domain.Specification;

import com.wow.be.framework.Condition.CondType;
import com.wow.be.framework.persist.SearchSpec;

public class ScheduleJobExecHistorySpec extends SearchSpec<ScheduleJobExecHistoryEntity> {

	public Specification<ScheduleJobExecHistoryEntity> withJobName(CondType condType, String jobName) {
		return super.with("jobName", condType, jobName);
	}
	
	public Specification<ScheduleJobExecHistoryEntity> withExecutionTime(CondType condType, String executionTime) {
		return super.with("executionTime", condType, executionTime);
	}

	public Specification<ScheduleJobExecHistoryEntity> withExecutionResult(CondType condType, String executionResult) {
		return super.with("executionResult", condType, executionResult);
	}
	
	public Specification<ScheduleJobExecHistoryEntity> withNextFireTime(CondType condType, String nextFireTime) {
		return super.with("nextFireTime", condType, nextFireTime);
	}
	
	public Specification<ScheduleJobExecHistoryEntity> withRemark(CondType condType, String remark) {
		return super.with("remark", condType, remark);
	}
}

