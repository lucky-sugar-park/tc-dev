package com.wow.be.tc.schedule.impl;

import com.wow.be.framework.persist.PersistentEntity;
import com.wow.be.tc.schedule.ScheduleJobDetailInfo;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString
@Builder
@Entity
@Table(name="SCHEDULE_JOB_DETAIL")
@Data
@EqualsAndHashCode(callSuper=false)
@AllArgsConstructor
public class ScheduleJobDetailEntity  extends PersistentEntity<ScheduleJobDetailInfo> {

	private static final long serialVersionUID = 1L;

	@EmbeddedId
	private ScheduleJobDetailEntityId entityId;

	@ManyToOne
//	@MapsId("scheduleJobId")
	@JoinColumn(name="SCHEDULE_JOB_ID", referencedColumnName="ID", nullable=false, updatable=false, insertable=false)
	private ScheduleJobEntity scheduleJob;
	
	@Column(name="ENTRY_VALUE")
	private String entryValue;
	
	@Column(name="ENTRY_VALUE_TYPE")
	private String entryValueType;
	
	@Column(name="DESCRIPTION", length=1024)
	private String description;
	
	public ScheduleJobDetailEntity() {
		super();
	}

	@Override
	public ScheduleJobDetailInfo info() {
		ScheduleJobDetailInfo info = new ScheduleJobDetailInfo();
		info.setScheduleJobId(this.entityId.getScheduleJobId());
		info.setEntryKey(this.entityId.getEntryKey());
		info.setEntryValue(this.getEntryValue());
		info.setEntryValueType(this.getEntryValueType());
		info.setDescription(this.getDescription());
		info.setCreatedBy(super.getCreatedBy());
		info.setCreatedDate(super.getCreatedDate());
		info.setUpdatedBy(super.getUpdatedBy());
		info.setUpdatedDate(super.getUpdatedDate());

		return info;
	}

	@Override
	public void from(ScheduleJobDetailInfo info) {
		ScheduleJobDetailEntityId entityId=new ScheduleJobDetailEntityId();
		entityId.setScheduleJobId(info.getScheduleJobId());
		entityId.setEntryKey(info.getEntryKey());
//		this.setScheduleJobId(info.getScheduleJobId());
		this.setEntityId(entityId);
		this.setEntryValue(info.getEntryValue());
		this.setEntryValueType(info.getEntryValueType());
		this.setDescription(info.getDescription());
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
