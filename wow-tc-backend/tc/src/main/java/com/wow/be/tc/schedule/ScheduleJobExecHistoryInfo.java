package com.wow.be.tc.schedule;

import java.util.Date;

import com.wow.be.framework.AbstractInfo;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper=false)
public class ScheduleJobExecHistoryInfo extends AbstractInfo {
	
	private long seqNo;
	
	private String jobName;
	
	private String schedulerName;
	
	private Date executionTime;
	
	private String executionResult;
	
	private Date nextFireTime;
	
	private String remark;

	@Override
	public String toPlainText() {
		return null;
	}

}
