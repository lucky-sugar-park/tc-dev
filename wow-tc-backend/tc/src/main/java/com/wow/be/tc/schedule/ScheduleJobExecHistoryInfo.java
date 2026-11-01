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
	
	/** 실행한 Quartz 인스턴스(instanceId) — 다중화에서 어느 서버가 돌렸는지 */
	private String instanceId;
	
	private Date executionTime;
	
	private String executionResult;
	
	private Date nextFireTime;
	
	/** 걸린 시간(ms) */
	private Long elapsedMs;
	
	private String remark;

	@Override
	public String toPlainText() {
		return null;
	}

}
