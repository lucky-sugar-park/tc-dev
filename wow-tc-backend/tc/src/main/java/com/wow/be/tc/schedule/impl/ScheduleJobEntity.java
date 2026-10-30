package com.wow.be.tc.schedule.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.wow.be.framework.persist.PersistentEntity;
import com.wow.be.tc.schedule.ScheduleConstants;
import com.wow.be.tc.schedule.ScheduleJobInfo;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString
@Builder
@Entity
@Table(name="SCHEDULE_JOB")
@Data
@EqualsAndHashCode(callSuper=false)
@AllArgsConstructor
public class ScheduleJobEntity extends PersistentEntity<ScheduleJobInfo> {

	private static final long serialVersionUID = 1L;

	@Id
	@Column(name="ID")
	private String id;
	
	@Column(name="NAME", unique=true)
	private String name;
	
	@Column(name="SCHED_NAME")
	private String schedulerName;
	
	@Column(name="ISUSE")
	private Boolean use;

	@Column(name="JOB_CLASS_NAME")
	private String jobClassName;
	
	@Column(name="GROUP_NAME")
	private String groupName;
	
	@Column(name="TRIGGER_STATE")
	@Builder.Default
	private String triggerState=ScheduleConstants.TRIGGER_STATE_NONE;
	
	@Column(name="STATE_CHANGED_AT")
	@Builder.Default
	private Date stateChangedTime=new Date();
	
	@Column(name="TRIGGER_TYPE")
	@Builder.Default
	private String triggerType=ScheduleConstants.TRIGGER_TYPE_CRON;
	
	@Column(name="START_DELAY")
	private Long startDelay;
	
	@Column(name="REPEAT_INTERVAL")
	private Long repeatInterval;
	
	@Column(name="CRON_EXPRESSION")
	private String cronExpression;
	
	@OneToMany (fetch=FetchType.EAGER, mappedBy="scheduleJob", cascade={ CascadeType.ALL })
//	@JoinColumn(name="SCHEDULE_JOB_ID", insertable=true, updatable=true)
	private List<ScheduleJobDetailEntity> jobDetails;
	
	@Column(name="DESCRIPTION", length=2048)
	private String description;
	
	public ScheduleJobEntity() {
		super();
		jobDetails=new ArrayList<>();
	}

	@Override
	public ScheduleJobInfo info() {
		ScheduleJobInfo info=new ScheduleJobInfo();
		info.setId(this.getId());
		info.setName(this.getName());
		info.setSchedulerName(this.getSchedulerName());

		if(this.getUse()!=null) {
			info.setUse(this.getUse());	
		}
		info.setJobClassName(this.getJobClassName());
		info.setGroupName(this.getGroupName());
		info.setTriggerState(this.getTriggerState());
		info.setTriggerType(this.getTriggerType());
		info.setStateChangedTime(this.getStateChangedTime());
		info.setStartDelay(this.getStartDelay());
		info.setRepeatInterval(this.getRepeatInterval());
		info.setCronExpression(this.getCronExpression());
		info.setDescription(this.getDescription());
		
		// set details
		jobDetails.forEach(detail->{
			info.addJobDetail(detail.info());
		});
		
		info.setCreatedBy(super.getCreatedBy());
		info.setCreatedDate(super.getCreatedDate());
		info.setUpdatedBy(super.getUpdatedBy());
		info.setUpdatedDate(super.getUpdatedDate());

		return info;
	}

	@Override
	public void from(ScheduleJobInfo info) {
		this.setId(info.getId());
		if(info.getName()!=null && !"".equals(info.getName())) {
			this.setName(info.getName());
		}
		if(info.getSchedulerName()!=null && !"".equals(info.getSchedulerName())) {
			this.setSchedulerName(info.getSchedulerName());
		}
		if(info.getUse()!=null) {
			this.setUse(info.getUse());
		}
		if(info.getJobClassName()!=null && !"".equals(info.getJobClassName())) {
			this.setJobClassName(info.getJobClassName());
		}
		if(info.getGroupName()!=null && !"".equals(info.getGroupName())) {
			this.setGroupName(info.getGroupName());
		}
		if(info.getTriggerState()!=null && !"".equals(info.getTriggerState())) {
			this.setTriggerState(info.getTriggerState());
		}
		if(info.getStateChangedTime()!=null) {
			this.setStateChangedTime(info.getStateChangedTime());
		}
		if(info.getTriggerType()!=null && !"".equals(info.getTriggerType())) {
			this.setTriggerType(info.getTriggerType());
		}
		
		if(info.getStartDelay()!=null) {
			this.setStartDelay(info.getStartDelay());
		}
		
		if(info.getRepeatInterval()!=null) {
			this.setRepeatInterval(info.getRepeatInterval());
		}
		
		if(info.getCronExpression()!=null && !"".equals(info.getCronExpression())) {
			this.setCronExpression(info.getCronExpression());
		}
		
		if(info.getDescription()!=null && !"".equals(info.getDescription())) {
			this.setDescription(info.getDescription());
		}
		
//		info.getJobDetails().forEach(jobDetail->{
//			ScheduleJobDetailEntity detailEntity=new ScheduleJobDetailEntity();
////			detailEntity.setScheduleJob(this);
//			jobDetail.setScheduleJobId(this.getId());
//			detailEntity.from(jobDetail);
//			jobDetails.add(detailEntity);
//		});
		
		super.setCreatedBy(info.getCreatedBy());
		super.setCreatedDate(info.getCreatedDate());
		super.setUpdatedBy(info.getUpdatedBy());
		super.setUpdatedDate(info.getUpdatedDate());		
	}

	@Override
	public String toPlainText() {
		return null;
	}	
}
