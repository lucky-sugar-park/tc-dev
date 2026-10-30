package com.wow.be.tc.schedule;

import com.wow.be.framework.AbstractInfo;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper=false)
public class ScheduleJobDetailInfo extends AbstractInfo {

	private String oper;
	
	private String scheduleJobId;
	
	private String entryKey;
	
	private String entryValue;
	
	private String entryValueType;
	
	private String description;
	
	@Override
	public String toPlainText() {
		return null;
	}	
}