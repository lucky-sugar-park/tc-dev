package com.wow.be.tc.schedule;

import com.wow.be.framework.Condition;

import lombok.Data;

@Data
public class JobDeletionInfo {

	private String deleteJobName;
	
	private Condition searchCondition;
}
