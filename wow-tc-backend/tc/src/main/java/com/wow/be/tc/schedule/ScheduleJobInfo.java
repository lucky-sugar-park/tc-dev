package com.wow.be.tc.schedule;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.quartz.Job;

import com.wow.be.framework.AbstractInfo;
import com.wow.be.tc.LegacyClassNames;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper=false)
public class ScheduleJobInfo extends AbstractInfo {

	private String id;
	
	private String name;
	
	private String schedulerName;
	
	private Boolean use;
	
	private Class<Job> jobClass;
	
	private String jobClassName;
	
	private String groupName;
	
	private String triggerState=ScheduleConstants.TRIGGER_STATE_NONE;
	
	private Date stateChangedTime=new Date();
	
	private String triggerType=ScheduleConstants.TRIGGER_TYPE_CRON;
	
	private Long startDelay;
	
	private Long repeatInterval;
	
	private String cronExpression;
	
	private List<ScheduleJobDetailInfo> jobDetails;
	
	private String description;
	
	public ScheduleJobInfo() {
		jobDetails=new ArrayList<>();
	}
	
	@SuppressWarnings("unchecked")
	public void setJobClassName(String jobClassName) {
		this.jobClassName = jobClassName;
		try {
			this.setJobClass((Class<Job>) (LegacyClassNames.forName(jobClassName)));
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	public void addJobDetail(ScheduleJobDetailInfo jobDetail) {
		jobDetails.add(jobDetail);
	}

	@Override
	public String toPlainText() {
		return null;
	}
}
